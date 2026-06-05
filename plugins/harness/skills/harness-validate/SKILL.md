---
name: harness-validate
description: >-
  Validate target-owned repository harness assets against the installed build/runtime contract. Use when verifying a fresh harness install, checking target harness changes before commit or CI, or diagnosing WARN and ERROR output from stack validators without requiring plugin-root structural agents in the target repository.
argument-hint: '[auto|gradle|maven|uv|bun|shell]'
disable-model-invocation: true
allowed-tools:
  - Bash(./gradlew *)
  - Bash(gradle *)
  - Bash(mvn *)
  - Bash(uv *)
  - Bash(bun *)
  - Bash(sh scripts/check.sh)
  - Read
  - Grep
  - Glob
  - LS
---

# Harness Validate

Run the native harness validation command for the target repository. This plugin skill is the visible runtime surface for validation guidance; it checks target-owned harness assets copied from `skills/harness-install/assets/` and reports whether the repository harness remains mechanically checkable.

## Ownership Boundary

- This skill owns validation guidance for installed target harness assets.
- Installed target agents and project skills remain target-owned runtime assets.
- Plugin-root structural agents are not target repository assets and are not required by target validation.

## First Safe Checks

1. Read `docs/harness/README.md` to confirm the selected stack and validation command.
2. Confirm the target stack from the argument or repository files.
3. Check that validation is being run from the target repository root; uv, bun, and Maven validators bind the current working directory as the target root.
4. Prefer the stack command documented in `docs/harness/README.md` over ad hoc checks.
5. Inspect `.github/workflows/<tool>.yaml` or `.gitlab-ci.yml` when validation is being checked through CI, but run local validation from the target root first.
6. Open `docs/harness/README.md` when command selection is unclear.

## Preflight

Check these support surfaces before running the stack command.

| Path | Use it for | Failure example |
| --- | --- | --- |
| `docs/harness/README.md` | Selected validation command and stack notes | `docs/harness/README.md: missing harness file - run harness-install or restore target-owned README` |
| `.github/workflows/<tool>.yaml` | GitHub Actions command parity (when present) | `.github/workflows/<tool>.yaml: CI command mismatch - expected generated pre-push final check command` |
| `.gitlab-ci.yml` | GitLab CI command parity | `.gitlab-ci.yml: CI command mismatch - expected generated pre-push final check command` |

## Mode Detection

Choose exactly one mode unless the user explicitly asks for cross-stack analysis.

| Evidence | Mode | Notes |
| --- | --- | --- |
| `settings.gradle`, `settings.gradle.kts`, `build.gradle`, or `build.gradle.kts` | `gradle` | Prefer `./gradlew` when executable. |
| `pom.xml` | `maven` | Maven project with Spotless validation. |
| `uv.lock` or Python `pyproject.toml` | `uv` | Run through `uv` so dependencies and Python version resolution stay target-owned. |
| `bun.lock`, `bun.lockb`, or JavaScript `package.json` without a stronger stack signal | `bun` | Use only when Bun is the intended project runtime. |
| Shell scripts, `Makefile`, or no stronger stack signal | `shell` | Shell-script-only or Makefile-driven project. |
| Multiple stack signals | explicit user mode | Report ambiguous stack when non-interactive, or use the installed README command if present. |

## Stack Commands

| Mode | Use when | Command |
| --- | --- | --- |
| `gradle` | `settings.gradle(.kts)` or `build.gradle(.kts)` exists | `./gradlew ktlintCheck`, or `gradle ktlintCheck` when the target uses system Gradle without a wrapper |
| `maven` | `pom.xml` exists | Maven Spotless with `git ls-files` and `spotlessFiles` |
| `uv` | `uv.lock` or Python `pyproject.toml` exists | `uv run scripts/check.py` |
| `bun` | `bun.lock`, `bun.lockb`, or `package.json` exists | `bun run check` |
| `shell` | shell-script-only or Makefile-driven repository | `sh scripts/check.sh` |

The installed README command is the local harness validation command. The generated `docs/harness/git-hooks/pre-commit` and `pre-push` command markers both carry this same stack validation command listed above.

## Command Examples

Run from the target repository root.

```sh
./gradlew ktlintCheck
```

```sh
gradle ktlintCheck
```

```sh
root=$(pwd -P); files=$(git ls-files -- "*.java" | while IFS= read -r file; do case "$file" in *,*) echo "error: Java path contains comma and cannot be represented in spotlessFiles: $file" >&2; exit 1;; esac; printf '%s/%s\n' "$root" "$file" | sed 's/[][\\.^$*+?{}()|]/\\&/g; s/^/^/; s/$/$/'; done | paste -sd, -); if [ -z "$files" ]; then mvn validate; echo "spotless: no tracked Java files to check"; else mvn validate spotless:check -DspotlessFiles="$files"; fi
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

Do not suppress validator output.

```sh
uv run scripts/check.py
```

Reject any pattern that discards validator output or forces a successful exit after failure because it hides diagnostics and turns failure into success.

## Workflow

1. Detect mode when the argument is empty or `auto`.
2. Run exactly one stack command from the table.
3. If validation fails, classify the failure using the table below, including directory, docs, agent, skill, hook, CI, generated-artifact, symlink, shebang, and stack-tool failures.
4. Fix only issues in the user's requested scope; otherwise report the failing contract and the owning file.
5. Re-run the same validation command after any fix.

If `auto` finds multiple stack signals and no installed README command resolves the selection, report ambiguity instead of guessing.

```text
mode: auto
result: fail (ambiguous stack)
failures:
- repository root: stack detection - found Gradle and Maven signals; rerun with `gradle` or `maven`
```

## Decisions

| Situation | Action |
| --- | --- |
| Missing target-owned harness file | Report the path, or rerun `harness-install` only if the user asked for repair. |
| Placeholder docs remain generic | Report exact files; do not invent product facts. |
| Hook command mismatch | Report unless the user explicitly approved hook changes. |
| CI command mismatch | Align CI only when CI files are in scope; otherwise report expected and actual commands. |
| Stack-tool failure before harness checks run | Report native tool failure separately from harness contract health. |
| Multiple stack signals in `auto` | Use installed README command when present; otherwise fail with explicit mode choices. |

## Failure Classification

| Category | Evidence | Smallest valid action |
| --- | --- | --- |
| Missing harness file | Validator names an absent `AGENTS.md`, `docs/harness/**`, docs file, agent, or skill | Re-run installation or restore the missing target-owned file. |
| Missing harness directory | Validator names an absent docs, `.claude/agents`, `.claude/skills`, or template directory | Restore the directory and required `.gitkeep` files when empty. |
| Stale placeholder | File exists but still contains generic scaffold content | Replace placeholder with target truth; do not invent product facts. |
| Agent or skill metadata | Agent or skill frontmatter lacks required `name` or `description` | Fix the specific agent or skill metadata. |
| Documentation contract | Required doc headings, generated-artifact semantics, or harness evolution wording is missing | Restore the documented contract in the named file. |
| Generated artifact metadata | A file under `docs/generated/` lacks source command, source inputs, freshness, or regeneration trigger metadata | Add metadata or remove the invalid generated artifact from scope. |
| Symlink safety | Validator reports symlink scan entries under protected harness paths | Replace symlinks with regular files or directories owned by the target. |
| Script permission | Generated hook or validator exists but is not executable where execution is required | Restore executable bit on the target-owned script. |
| Script shebang | Executable script does not use `/usr/bin/env` shebang | Update the executable script shebang. |
| Unsupported validation command | Generated `pre-push` declares a command outside the installer allow-list | Regenerate selected-mode hook content from the installer. |
| Hook wiring | Generated `pre-commit` does not match the stack-specific hook contract, generated `pre-push` lacks the selected final check command, or CI drifts from generated `pre-push` | Regenerate selected-mode hook content only with explicit hook approval. |
| CI command mismatch | `.github/workflows/<tool>.yaml` or `.gitlab-ci.yml` uses a different validation command | Align CI with the generated pre-push final check command. |
| Stack-tool failure | The native tool exits before harness checks run | Report the tool failure separately from harness contract health. |

## CI Checks

Local validation is the source of truth. CI files are examples that should run the generated pre-push final check command.

| CI file | Expected validation shape |
| --- | --- |
| `.github/workflows/<tool>.yaml` | One job whose run step matches the generated pre-push final check command. |
| `.gitlab-ci.yml` | One `harness` job whose script entry matches the generated pre-push final check command. |

If CI is skipped during install, report that local validation passed and CI snippets were intentionally absent.

GitHub-only and GitLab-only repositories may intentionally delete the unused CI file as a post-install step. When `git remote -v` shows only one host and the matching CI file is missing or out of sync with the generated pre-push command, report it as `not active` rather than `mismatch`; report the still-present CI file under the normal parity table.

Report CI drift with the expected command.

```text
ci: mismatch - .github/workflows/ultracite.yaml runs `bun run format`, expected `bun run check`
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

`SEVERITY` is one of `ERROR`, `WARN`, or `INFO`. `category` matches the rule identifier.

## Safe-Format Conventions

Format commands apply fixes through their native tools. All fixes are idempotent: a second run immediately after the first produces no additional modifications. Format commands run validation after applying fixes, print remaining findings, and fail when any remaining finding has `ERROR` severity.

## Invariants

- Validation checks the target repository harness, not this plugin package.
- The installed README and native-tool contracts define the expected target repository contract.
- Stack validators invoke native ecosystem tools (ktlint, Spotless, ruff, ultracite, shellcheck).
- File presence alone does not prove project readiness when placeholders still lack project-specific content.
- Generated artifacts are valid only when they document source command, source inputs, freshness, and regeneration trigger.
- GitHub Actions, GitLab CI, and the generated `pre-commit` and `pre-push` hooks MUST remain examples of the same selected stack validation command.
- Check and validation findings MUST use the canonical diagnostic prefix with one-based line and column numbers when position is available.

## Pitfalls

- Do not skip validation because installation printed a command successfully.
- Do not run every stack command; use the matching ecosystem.
- Do not make unrelated product-code changes to satisfy harness validation.
- Do not remove required harness files to get a green result.
- Do not redirect validator output away; WARN and ERROR output is part of the proof.
- Do not force a successful exit after validation failure.

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
- `result`: pass or fail with exit status when available.
- `failures`: file paths and contract categories for any failures.
- `ci`: whether GitHub Actions or GitLab CI snippets match the selected command when present.
- `next action`: smallest valid fix or explicit reason no fix was made.
