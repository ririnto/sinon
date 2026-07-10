---
name: harness-install
description: >-
  Install repository harness assets from the plugin asset package.
  Use when setting up or refreshing `AGENTS.md`, `CLAUDE.md`, `ARCHITECTURE.md`, `WORKFLOW.md`, docs, project agents, project skills, validators, CI files, and Git hook templates.
---

# Harness Install

Install or refresh repository harness files from this plugin.
This plugin skill is the runtime surface for installation guidance and orchestration.
Files that live inside a target repository are packaged under `skills/harness-install/assets/`.
They become project files after copying.

## Role

`harness-install` is the installer boundary:

- choose the installed mode (`bun`, `gradle`, `maven`, `shell`, `uv`)
- copy role-partitioned artifacts
- optionally activate stack hook state after explicit approval
- report installation + validation outcomes

Target business logic and day-to-day feature code stay in the target repository.

## Ownership Boundary

- This skill owns installation guidance and orchestration.
- Target repository agents, project skills, docs, validators, CI files, and hook scaffolds come from `skills/harness-install/assets/`.
- Plugin-root structural agents are not installable target assets.

## First Safe Checks

1. Confirm the target repository working tree is clean before running the installer.
   - Run `git status --short` in the target; if the output is non-empty, stop and ask the user to commit or stash first.
   - The installer writes new files and overwrites existing ones when `--force` is used.
   - Commit-first keeps the installation set isolated and reversible.
2. Inspect existing target files when present: `AGENTS.md`, `ARCHITECTURE.md`, `WORKFLOW.md`, and `README.md`.
   - Check existing `CLAUDE.md` imports `AGENTS.md`.
3. Determine the target stack from explicit user choice.
   - The installer no longer auto-detects.
   - Confirm the stack with the user before passing `--mode`.
     - Inspect manifests in the target root.
       - Examples: `pom.xml`, `build.gradle*`, `pyproject.toml`/`uv.lock`, `package.json`/`bun.lock`, and `Makefile`/`*.sh`.
4. Identify the active CI host.
   - Use `git remote -v` to confirm whether the project ships through GitHub, GitLab, both, or neither.
   - Remove unused CI files as a post-install step.
5. Every stack ships executable POSIX hooks under `.githooks/`.
   - `pre-commit` runs the selected canonical check.
   - `pre-push` runs the selected final gate.
   - Copying hooks does not activate them.
   - Ordinary stack setup and build commands MUST NOT change Git hook configuration.
   - Pass `--activate-hooks` only after explicit approval.
   - Activation runs `git config --local core.hooksPath .githooks/` and verifies both hooks.
6. Use `--force` only when the user explicitly wants existing target harness files replaced.
7. Keep plugin files separate from target files.
   - Edit this plugin only when improving the installer.
   - Edit target `.claude/**` files only after installation in the target repository.

## Workflow

1. Choose an explicit mode (no auto-detection).

    ```text
    gradle
    maven
    uv
    bun
    shell
    ```

2. Run the installer with the target repository as `--target` and an explicit `--mode`.

    ```sh
    "${CLAUDE_PLUGIN_ROOT:-/path/to/sinon/plugins/harness}/skills/harness-install/scripts/install-harness.ts" \
      --target "$PWD" \
      --mode gradle \
      --ci-host github
    ```

3. Run the selected-mode validation command printed by the installer before reporting completion.
4. Inspect changed files and distinguish kept files from written files in the final report.
5. Report hook behavior, validation status, and any files the user must fill with project truth.

## Installer Flags

The installer entry point `skills/harness-install/scripts/install-harness.ts` accepts these flags.

| Flag | Purpose |
| --- | --- |
| `--target <path>` | Target repository root to install into (defaults to `.`). |
| `--mode <gradle\|maven\|uv\|bun\|shell>` | Required stack mode; the installer does not auto-detect. |
| `--ci-host <github\|gitlab\|both\|none>` | Required CI host selection. |
| `--force` | Overwrite or update existing target files; required to resolve drift on root contracts. |
| `--activate-hooks` | Activate both POSIX hook files through repository-local `core.hooksPath`; fail if either file, its executable bit, its header, or Git configuration is invalid. |
| `--adopt <path>` | Preserve one legitimate target-owned evolution without changing its bytes, merge `kept`/`target` ownership into a complete record, and make later refreshes preserve it. |
| `--preview` | Print the planned write/keep/overwrite/drift set without writing anything. |
| `--show <path>` | Print the asset that would be installed at one target path. |
| `--only <path>` | Install one target path and merge its outcome into the install record. A first-time targeted install creates an explicitly partial record. |

`--preview` reports drift explicitly.
A target file that differs from its template is reported as `drift` and names both safe resolutions: `--adopt <path>` preserves target truth, while `--force` overwrites it.
A successful preview never silently claims a refresh it did not perform.

A full install writes `.harness/install-record.json` with the mode, CI host, canonical commands, exact expected plan, completeness, and each asset's actual `created`, `updated`, `kept`, or `conflict` outcome.
Each outcome also records `harness`, `shared`, or `target` ownership.
Later refreshes update an unchanged harness-owned file when its plugin source changes.
Target drift remains a conflict until the maintainer either overwrites it with `--force` or preserves it with `--adopt <path>`.
Adoption changes only the record; it never changes target bytes.
Root contracts remain shared managed-block files and cannot be adopted.

A first-time `--only` record remains explicitly partial until a full install establishes the complete inventory; do not report it as a complete harness installation.

## Asset Role Map

- `skills/harness-install/assets/common/AGENTS.md`: root contract.
- `skills/harness-install/assets/common/ARCHITECTURE.md`: architecture and component roles.
- `skills/harness-install/assets/common/WORKFLOW.md`: process rules.
- `skills/harness-install/assets/common/docs/**`: operating and domain context.
- `skills/harness-install/assets/<mode>/**`: stack validation and hook assets for the chosen mode.
- `skills/harness-install/assets/common/.claude/agents/scoped-implementer.md`: Haiku low-effort subagent for exact edits with an exhaustive file ownership list, desired behavior, and targeted validation commands.
- `skills/harness-install/assets/common/.claude/agents/implementation.md`: Sonnet medium-effort subagent for large or cross-file work that requires affected-set discovery, reasoning, and integration validation.
- `skills/harness-install/assets/common/.claude/agents/review.md`: independent read-only review subagent.
- `skills/harness-install/assets/common/.codex/agents/scoped-implementer.toml`: Luna low-effort counterpart for exact exhaustive file scopes.
- `skills/harness-install/assets/common/.codex/agents/implementation.toml`: Terra medium-effort counterpart for broad or cross-file implementation.
- `skills/harness-install/assets/common/.claude/skills/autonomous-execution/SKILL.md`: orchestration loop for explicit autonomous follow-through.
- `skills/harness-install/assets/common/.claude/skills/issue-mining/SKILL.md`: investigation and issue-registration skill.

## Decisions

| Situation | Action |
| --- | --- |
| Target has no obvious stack files | Ask for `gradle`, `maven`, `uv`, `bun`, or `shell`. |
| Existing harness files are present | Preserve them unless `--force` was requested. |
| Existing Git hook is present | The installer copies `.githooks/` but leaves current Git configuration unchanged by default. |
| | Pass `--activate-hooks` only after reviewing the existing hook policy. |
| | Request explicit approval before changing active hooks directly. |
| Legitimate target evolution conflicts with the plugin source | Run `--adopt <path>` to preserve the target bytes and record target ownership. |
| | Re-run install-record validation, then a full refresh to prove the adopted path remains stable. |
| Target uses GitHub CI | Pass `--ci-host github` to write only `.github/workflows/<tool>.yaml`. |
| Target uses GitLab CI | Pass `--ci-host gitlab` to write only `.gitlab-ci.yml`. |
| Target mirrors to GitHub and GitLab | Pass `--ci-host both` to write both CI files. |
| | Both CI scripts must run the same canonical check command. |
| Target has a no-CI policy | Pass `--ci-host none`. |
| No CI host is specified | Request an explicit choice: GitHub, GitLab, both, or none. |
| | Pass `--ci-host <github\|gitlab\|both\|none>` to the installer. |
| Complex Gradle settings already exist | Review `settings.gradle(.kts)` after installer wiring. |
| | Composite build or plugin-management blocks may need manual adjustment. |
| Installed placeholders are still generic | Treat installation as incomplete readiness. |
| | Tell the user which docs need target content. |

## Invariants

- Copied harness files become project files.
- Git hooks are opt-in for every stack:
  - Each mode ships `.githooks/pre-commit` and `.githooks/pre-push` with the POSIX hook header.
  - Installation and ordinary dependency/build setup MUST leave hooks inactive.
  - `--activate-hooks` configures repository-local `core.hooksPath` as `.githooks/`.
- `pre-commit` hooks run the stack lint check.
  - Bun always runs packaged markdownlint; non-Bun stacks run markdownlint when `markdownlint-cli2` is installed.
  - `pre-push` hooks run the full validation including tests and broader checks where applicable.
    - Examples: `./gradlew check`, `./mvnw verify`, and `bun test --pass-with-no-tests`.
- Claude worktrees use Claude Code's default Git worktree behavior.
  - The selected stack supplies `.worktreeinclude` for portable gitignored local inputs.
  - The target `.gitignore` ignores `.claude/worktrees/`.
  - The selected stack supplies `.claude/settings.json` with async `hooks.PostToolUse[]` entries matching the `EnterWorktree` tool.
  - These hooks run `codegraph init; codegraph index` and the stack install command from the worktree directory.
  - Bun uses `bun install`.
  - uv uses `uv sync`.
  - Gradle uses `./gradlew help`.
  - Maven uses `./mvnw -q -DskipTests dependency:go-offline`.
  - Claude Code reports hook failures.
  - It still uses the default worktree creation path.
- `.mcp.json` configures the project-local CodeGraph MCP server.
- `.codegraph/.gitignore` keeps CodeGraph local data out of Git.
- Target repositories use `AGENTS.md` as the primary harness contract.
- `CLAUDE.md` remains a pointer document that imports `AGENTS.md`.
- `docs/generated/` is a generated-artifact location for real generated outputs.
- Plugin skills install, validate, and evolve the harness package.
- Installed skills and agents guide day-to-day work inside the target repository.
- Stack-specific check and fix commands run native ecosystem tools:
  - Gradle uses `./gradlew ktlintCheck` and `./gradlew ktlintFormat` through ktlint project discovery.
  - Maven uses Checkstyle plus Spotless with `git ls-files` and `spotlessFiles`.
  - uv uses `uv run scripts/check.py` and `uv run scripts/fix.py` through Ruff project discovery.
  - Bun uses `bun run check` and `bun run fix`.
  - Shell uses `sh scripts/check.sh` and `sh scripts/fix.sh`.

## Operating Checks

- Run the installer on a committed or stashed target working tree.
  - `--force` overwrites tracked files, and a clean starting point keeps rollback separate from unrelated edits.
- Keep `scripts/install-harness.ts` changes in plugin evolution work.
- Report project readiness only after target truth exists.
  - Product specs, architecture decisions, acceptance criteria, and generated artifacts must all contain target truth.
- Replace hook files with `--force` after explicit approval for that repository.
- Adopt a legitimate target-owned file with `--adopt <path>` instead of overwriting it.
- Treat seed references as replaceable starting points.
- Keep runtime services, issue queues, workspace management, and team generation in project systems.

## Output Contract

Report these fields:

- `mode`: explicit stack mode (`--mode` flag).
- `installer command`: the command that ran.
- `files`: written, kept, and skipped file groups.
- `hooks`: whether copied `.githooks/` remain inactive or were explicitly activated through repository-local `core.hooksPath`.
- `ci`: which CI files were installed (GitHub Actions, GitLab CI, both, or none per `--ci-host` flag).
- `validation`: command run and result.
- `target follow-up`: placeholders, seed references, or unused CI files that require project-specific action.

## References

- `references/rule-interface.md`: open it when changing a shipped validator, fix command, custom rule, EditorConfig knob, hook command, or CI command, and you need the exact native enforcement shape (validator/fix commands, rule ids, or the prose-only structural checks) for one stack before editing.
