---
name: harness-validate
description: >-
  Validate repository harness assets against the installed build/runtime contract.
  Use when verifying a fresh harness install, checking target harness changes before commit or CI, or diagnosing WARN and ERROR output from stack validators without requiring plugin-root structural agents in the target repository.
---

# Harness Validate

Run the native harness validation command for the target repository.
This plugin skill is the visible runtime surface for validation guidance; it checks harness assets copied from `skills/harness-install/assets/` and reports whether the repository harness remains mechanically checkable.

## Ownership Boundary

- This skill owns validation guidance for installed target harness assets.
- Installed target agents and project skills remain project runtime assets.
- Plugin-root structural agents are not target repository assets and are not required by target validation.

## First Safe Checks

1. Confirm the selected stack and validation command from the caller, installer output, or target files.
2. Confirm the target stack from the argument or repository files.
3. Check that validation is being run from the target repository root; uv, bun, and Maven validators bind the current working directory as the target root.
4. Validate `.harness/install-record.json` before selecting or running the native stack command.
5. Use the selected stack command for local validation.
6. Inspect `.github/workflows/<tool>.yaml` or `.gitlab-ci.yml` when validation is being checked through CI, but run local validation from the target root first.
7. Report ambiguity when command selection remains unclear.

## Preflight

Check these support surfaces before running the stack command.

| Path | Use it for | Failure example |
| --- | --- | --- |
| `WORKFLOW.md` | Selected validation command and Git host notes | `WORKFLOW.md: missing harness file - run harness-install or restore workflow` |
| `.github/workflows/<tool>.yaml` | GitHub Actions command parity (when present) | `.github/workflows/<tool>.yaml: CI command mismatch - expected installed canonical check command` |
| `.gitlab-ci.yml` | GitLab CI command parity | `.gitlab-ci.yml: CI command mismatch - expected installed canonical check command` |

## Mode Detection

Choose exactly one mode unless the user explicitly asks for cross-stack analysis.

| Evidence | Mode | Notes |
| --- | --- | --- |
| `settings.gradle`, `settings.gradle.kts`, `build.gradle`, or `build.gradle.kts` | `gradle` | Prefer `./gradlew` when executable. |
| `pom.xml` | `maven` | Maven project with Spotless validation. |
| `uv.lock` or Python `pyproject.toml` | `uv` | Run through `uv` so dependencies and Python version resolution stay local to the target project. |
| `bun.lock`, `bun.lockb`, or JavaScript `package.json` without a stronger stack signal | `bun` | Use only when Bun is the intended project runtime. |
| Shell scripts, `Makefile`, or no stronger stack signal | `shell` | Shell-script-only or Makefile-driven project. |
| Multiple stack signals | explicit user mode | Report ambiguous stack when non-interactive, or use the installed workflow command if present. |

## Stack Commands

| Mode | Use when | Command |
| --- | --- | --- |
| `gradle` | `settings.gradle(.kts)` or `build.gradle(.kts)` exists | `./gradlew ktlintCheck`, or `gradle ktlintCheck` when the target uses system Gradle without a wrapper |
| `maven` | `pom.xml` exists | Maven Spotless with `git ls-files` and `spotlessFiles` |
| `uv` | `uv.lock` or Python `pyproject.toml` exists | `uv run scripts/check.py` |
| `bun` | `bun.lock`, `bun.lockb`, or `package.json` exists | `bun run check` |
| `shell` | shell-script-only or Makefile-driven repository | `sh scripts/check.sh` |

The canonical check command in the table is the local harness validation command, matches the installed `pre-commit` hook, and is the command CI (both hosts) re-runs; the two CI hosts MUST agree with each other and with `pre-commit`.
The installed `pre-push` hook is a local-only stricter superset that adds tests on top of the canonical command: `./gradlew check` (gradle), `./mvnw verify` (maven), `bun run check && bun test` (bun), or the same canonical command for uv and shell; it is not required to match CI.
`.harness/install-record.json` persists canonical commands plus complete asset outcomes and ownership. Validate it from the target root before the native stack command:

```sh
bun "${CLAUDE_PLUGIN_ROOT}/skills/harness-validate/scripts/validate-install-record.ts" .
```

The self-contained preflight fails on a partial first-time `--only` record, unresolved conflict, harness-owned target drift, shared managed-block drift, missing ownership digest, command mismatch, duplicate path, or empty inventory. Target-owned content is not silently reclaimed.

## Command Examples

Run from the target repository root.

```sh
./gradlew ktlintCheck
```

```sh
gradle ktlintCheck
```

```sh
root=$(pwd -P); if git ls-files -- "*.java" | grep -q '^"'; then unsafe_file=$(git ls-files -- "*.java" | grep '^"'); echo "error: escaped Java path for spotlessFiles: $unsafe_file" >&2; exit 1; fi; if git ls-files -- "*.java" | grep -q ','; then comma_file=$(git ls-files -- "*.java" | grep ','); echo "error: comma Java path for spotlessFiles: $comma_file" >&2; exit 1; fi; files=$(git ls-files -- "*.java" | while IFS= read -r file; do printf '%s/%s\n' "$root" "$file" | sed 's/[][\\.^$*+?{}()|]/\\&/g; s/^/^/; s/$/$/'; done | paste -sd, -); if [ -z "$files" ]; then ./mvnw validate; echo "spotless: no tracked Java files to check"; else ./mvnw validate -DspotlessFiles="$files"; fi
```

```sh
uv run scripts/check.py
```

```sh
bun run check
```

```sh
sh scripts/check.sh
```

Keep validator output visible.

```sh
uv run scripts/check.py
```

Reject any pattern that discards validator output or forces a successful exit after failure because it hides diagnostics and turns failure into success.

## Workflow

1. Detect mode when the argument is empty or `auto`.
   - If `auto` finds multiple stack signals and the installed workflow lacks a resolved selection, report ambiguity.
   - Choose a command only after the ambiguity is resolved.

     ```text
     mode: auto
     result: fail (ambiguous stack)
     failures:
     - repository root: stack detection - found Gradle and Maven signals; rerun with `gradle` or `maven`
     ```

2. Run exactly one stack command from the table.
3. If validation fails, classify the failure using the table below, including directory, docs, agent, skill, hook, CI, generated-artifact, symlink, shebang, and stack-tool failures.
4. Fix only issues in the user's requested scope; otherwise report the failing contract and the owning file.
5. Re-run the same validation command after any fix.

## Decisions

| Situation | Action |
| --- | --- |
| Missing harness file | Report the path, or rerun `harness-install` only if the user asked for repair. |
| Placeholder docs remain generic | Report exact files and request target facts. |
| Hook command mismatch | Report unless the user explicitly approved hook changes. |
| CI command mismatch | Align CI only when CI files are in scope; otherwise report expected and actual commands. |
| Stack-tool failure before harness checks run | Report native tool failure separately from harness contract health. |
| Multiple stack signals in `auto` | Use installed workflow command when present, or fail with explicit mode choices. |

## Failure Classification

| Category | Evidence | Smallest valid action |
| --- | --- | --- |
| Missing harness file | Validator names an absent `AGENTS.md`, `docs/**`, agent, or skill | Re-run installation or restore the missing file. |
| Missing harness directory | Validator names an absent docs, `.claude/agents`, `.claude/skills`, or template directory | Restore the missing directory. |
| Stale placeholder | File exists but still contains generic scaffold content | Replace placeholder with target truth or request target facts. |
| Agent or skill metadata | Agent or skill frontmatter lacks required `name` or `description` | Fix the specific agent or skill metadata. |
| Documentation contract | Required doc headings, generated-artifact semantics, or harness evolution wording is missing | Restore the documented contract in the named file. |
| Generated artifact metadata | A file under `docs/generated/` lacks source command, source inputs, freshness, or regeneration trigger metadata | Add metadata or remove the invalid generated artifact from scope. |
| Symlink safety | Validator reports symlink scan entries under protected harness paths | Replace symlinks with regular files or directories owned by the target. |
| Script permission | Installed hook or validator exists but is not executable where execution is required | Restore executable bit on the script. |
| Script shebang | Executable script does not use `/usr/bin/env` shebang | Update the executable script shebang. |
| Unsupported validation command | Installed `pre-push` declares a command outside the installer allow-list | Reinstall selected-mode hook content from the installer. |
| Hook wiring | Installed `pre-commit` does not match the stack-specific hook contract, installed `pre-push` lacks the selected final check command, or CI drifts from the installed canonical check command | Reinstall selected-mode hook content only with explicit hook approval. |
| CI command mismatch | `.github/workflows/<tool>.yaml` or `.gitlab-ci.yml` uses a different validation command | Align CI with the installed canonical check command (the `pre-commit` command). |
| Stack-tool failure | The native tool exits before harness checks run | Report the tool failure separately from harness contract health. |

## CI Checks

Local validation is the source of truth.
CI files are install-time assets that re-run the installed canonical check command (the `pre-commit` command), not the local `pre-push` superset.

| CI file | Expected validation shape |
| --- | --- |
| `.github/workflows/<tool>.yaml` | One job whose run step matches the installed canonical check command. |
| `.gitlab-ci.yml` | One `harness` job whose script entry matches the installed canonical check command. |

When install uses `--ci-host none`, report local validation status and the chosen no-CI policy.

GitHub-only and GitLab-only repositories may intentionally delete the unused CI file as a post-install step.
When `git remote -v` shows only one host and the matching CI file is missing or out of sync with the installed canonical check command, report it as `not active` rather than `mismatch`; report the still-present CI file under the normal parity table.

Report CI drift with the expected command.

```text
ci: mismatch - .github/workflows/ultracite.yaml runs `bun run fix`, expected `bun run check`
```

## Diagnostic Format

Check and validation output uses a canonical prefix so that agents, CI, and humans can parse findings uniformly.

Location-bearing findings use one-based line and column numbers:

```text
path:line:column [SEVERITY] category: message
```

When position is unavailable, validators fall back to shorter forms:

```text
[SEVERITY] category: repository-level message
```

```text
path/to/file [SEVERITY] category: file-level message
```

`SEVERITY` is one of `ERROR`, `WARN`, or `INFO`.
`category` matches the rule identifier.

## Safe-Format Conventions

Fix commands apply changes through their native tools.
Native formatting tools (ruff `format`, shfmt, ktlint `ktlintFormat`, Spotless `spotless:apply`, ultracite `fix`, markdownlint `--fix`) are idempotent for the subset they can rewrite; lint rules may still leave findings that require manual edits.
Not every fix command re-runs validation afterward, so treat the canonical check command as the source of truth: re-run it after any fix and use its exit status as the verdict rather than assuming the fix command already revalidated.

## Invariants

- Validation checks the target repository harness, not this plugin package.
- The installed README and native-tool contracts define the expected target repository contract.
- Stack validators invoke native ecosystem tools (ktlint, Spotless, ruff, ultracite, shellcheck).
- File presence alone does not prove project readiness when placeholders still lack project-specific content.
- Generated artifacts are valid only when they document source command, source inputs, freshness, and regeneration trigger.
- GitHub Actions, GitLab CI, and the installed `pre-commit` hook MUST run the same canonical check command, and the two CI hosts MUST agree with each other; the installed `pre-push` hook is a local-only superset that MAY add tests and is not required to match CI.
- Check and validation findings MUST use the canonical diagnostic prefix with one-based line and column numbers when position is available.

## Operating Checks

- Run validation after installation prints the command.
- Run the matching ecosystem command.
- Keep product-code fixes separate from harness validation fixes.
- Restore required harness files when validation names them.
- Keep WARN and ERROR output as proof.
- Preserve validation failure exits.

## Report Template

```text
mode: <gradle|maven|uv|bun|shell> (--mode flag)
command: <exact command>
result: <pass|fail> (<exit status or unavailable>)
failures:
- <path>: <category> - <evidence>
ci: <not checked|github ok|gitlab ok|mismatch - path>
next action: <smallest valid fix or reason no fix was made>
```

## Output Contract

Report these fields:

- `mode`: explicit stack mode (--mode flag).
- `command`: exact validation command.
- `result`: pass or fail with exit status when reported.
- `failures`: file paths and contract categories for any failures.
- `ci`: whether GitHub Actions or GitLab CI files match the installed canonical check command when present.
- `next action`: smallest valid fix or explicit reason no fix was made.
