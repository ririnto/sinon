---
name: harness-install
description: >-
  Install target-owned repository harness assets from the plugin asset package.
  Assets include `AGENTS.md` contract, `CLAUDE.md` pointer, `ARCHITECTURE.md`, docs structure, project agents, project skills, structured repository templates, language-matched validators, CI files, and Git hook templates.
  Use when setting up or refreshing a repository harness.
  Also use when adding target-owned Claude agents or skills to a repo.
  Also use when wiring validation commands for Gradle, Maven, uv, bun, or shell projects.
---

# Harness Install

Install or refresh target-owned repository harness files from this plugin.
This plugin skill is the visible runtime surface for installation guidance and orchestration.
Files that live inside a target repository are packaged under `skills/harness-install/assets/`.
They become target-owned after copying.

## Ownership Boundary

- This skill owns installation guidance and orchestration.
- Target repository agents, project skills, docs, validators, CI files, and hook scaffolds come from `skills/harness-install/assets/`.
- Plugin-root structural agents are not installable target assets.

## First Safe Checks

1. Confirm the target repository working tree is clean before running the installer.
   - Run `git status --short` in the target; if the output is non-empty, stop and ask the user to commit or stash first.
   - The installer writes new files and overwrites existing ones when `--force` is used.
   - Any rollback or recovery action after installation will be indistinguishable from unrelated in-progress work.
   - Commit-first keeps the harness change set isolated and reversible.
2. Read the target repository root files if present: `AGENTS.md`, `ARCHITECTURE.md`, `WORKFLOW.md`, and `README.md`.
   - Inspect `CLAUDE.md` only to confirm it imports `AGENTS.md`.
3. Determine the target stack from explicit user choice.
   - The installer no longer auto-detects.
   - Confirm the stack with the user before passing `--mode`.
     - Inspect manifests in the target root.
       - Examples: `pom.xml`, `build.gradle*`, `pyproject.toml`/`uv.lock`, `package.json`/`bun.lock`, and `Makefile`/`*.sh`.
4. Identify the active CI host.
   - Use `git remote -v` to confirm whether the project ships through GitHub, GitLab, both, or neither.
   - Remove unused CI files as a post-install step.
5. Git hooks are managed by ecosystem-standard tools:
   - Bun uses Husky.
   - uv uses the pre-commit framework.
   - Gradle uses the Gradle pre-commit plugin.
   - Maven and Shell use `core.hooksPath`.
   - Each stack ships static hook files in its asset tree.
     - Bun uses `.husky/`.
     - uv uses `.pre-commit-config.yaml`.
     - Gradle wires hooks from `settings.gradle.kts`.
     - Maven and Shell use `.githooks/`.
   - The installer copies these as regular stack assets and activates hooks as a post-install step.
     - Gradle hooks are created by the Gradle plugin on the first build.
     - Maven and Shell run `git config core.hooksPath .githooks/`.
     - uv runs `uv run pre-commit install`.
     - Bun runs Husky through the `prepare` script after `bun install`.
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
    "${CLAUDE_PLUGIN_ROOT:-/path/to/sinon/plugins/harness}/skills/harness-install/scripts/install-harness.py" \
      --target "$PWD" \
      --mode gradle \
      --ci-host github
    ```

    ```sh
    uv run "${CLAUDE_PLUGIN_ROOT:-/path/to/sinon/plugins/harness}/skills/harness-install/scripts/install-harness.py" \
      --target "$PWD" \
      --mode gradle \
      --ci-host github
    ```

3. Run the selected-mode validation command printed by the installer before reporting completion.
4. Inspect changed files and distinguish kept files from written files in the final report.
5. Report hook behavior, validation status, and any target-owned files the user must fill with project truth.

## Decisions

| Situation | Action |
| --- | --- |
| Target has no obvious stack files | Ask for `gradle`, `maven`, `uv`, `bun`, or `shell`. |
| Existing harness files are present | Preserve them unless `--force` was requested. |
| Existing Git hook is present | Each stack uses ecosystem-standard hook management. |
| | The installer activates hooks as a post-install step. |
| | Request explicit approval before changing active hooks directly. |
| Target uses GitHub CI | Pass `--ci-host github` to write only `.github/workflows/<tool>.yaml`. |
| Target uses GitLab CI | Pass `--ci-host gitlab` to write only `.gitlab-ci.yml`. |
| Target mirrors to GitHub and GitLab | Pass `--ci-host both` to write both CI files. |
| | The installed `pre-push` final check command must match both scripts. |
| Target has a no-CI policy | Pass `--ci-host none`. |
| No CI host is specified | Request an explicit choice: GitHub, GitLab, both, or none. |
| | Pass `--ci-host <github\|gitlab\|both\|none>` to the installer. |
| Complex Gradle settings already exist | Review `settings.gradle(.kts)` after installer wiring. |
| | Composite build or plugin-management blocks may need manual adjustment. |
| Installed placeholders are still generic | Treat installation as incomplete readiness. |
| | Tell the user which docs need target content. |

## Invariants

- The installed harness is target-owned after copying.
- Git hooks use ecosystem-standard tools:
  - Bun uses Husky with `.husky/`.
  - uv uses the pre-commit framework with `.pre-commit-config.yaml`.
  - Gradle uses the Gradle pre-commit plugin.
  - Maven and Shell use `core.hooksPath` pointing to `.githooks/`.
  - The installer activates hooks as a post-install step for Maven, Shell, and uv.
  - Gradle hooks are created by the Gradle plugin on the first build.
  - Bun hooks activate via `bun install` through the Husky `prepare` script.
- `pre-commit` hooks run the stack lint check.
  - Bun always runs packaged markdownlint; non-Bun stacks run markdownlint when `markdownlint-cli2` is installed.
  - `pre-push` hooks run the full validation including tests and broader checks where applicable.
    - Examples: `./gradlew check`, `./mvnw verify`, and `bun test`.
- Fresh installs use `AGENTS.md` as the primary target repository harness contract.
- `CLAUDE.md` remains a pointer document that imports `AGENTS.md`.
- `docs/generated/` is a generated-artifact location for real generated outputs.
- Plugin skills install, validate, and evolve the harness package.
- Installed target skills and agents guide day-to-day work inside the target repository.
- Stack-specific check and fix commands run native ecosystem tools:
  - Gradle uses `./gradlew ktlintCheck` and `./gradlew ktlintFormat` through ktlint project discovery.
  - Maven uses Checkstyle plus Spotless with `git ls-files` and `spotlessFiles`.
  - uv uses `uv run scripts/check.py` and `uv run scripts/fix.py` through Ruff project discovery.
  - Bun uses `bun run check` and `bun run fix`.
  - Shell uses `sh scripts/check.sh` and `sh scripts/fix.sh`.

## Operating Checks

- Run the installer on a committed or stashed target working tree.
  - `--force` overwrites tracked files, and a clean starting point keeps rollback separate from unrelated edits.
- Keep `scripts/install-harness.py` changes in plugin evolution work.
- Report project readiness only after target truth exists.
  - Product specs, architecture decisions, acceptance criteria, and generated artifacts must all contain target truth.
- Replace target-owned hook files with `--force` after explicit approval for that repository.
- Treat seed references as replaceable starting points.
- Keep runtime services, issue queues, workspace management, and team generation in target-owned systems.

## Output Contract

Report these fields:

- `mode`: explicit stack mode (`--mode` flag).
- `installer command`: the command that ran.
- `files`: written, kept, and skipped file groups.
- `hooks`: ecosystem tool activation status for the selected stack (Husky, pre-commit, or `core.hooksPath`).
- `ci`: which CI files were installed (GitHub Actions, GitLab CI, both, or none per `--ci-host` flag).
- `validation`: command run and result.
- `target follow-up`: placeholders, seed references, or unused CI files that require project-specific action.
