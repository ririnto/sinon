---
name: harness-install
description: >-
  Install target-owned repository harness assets from the plugin asset package: CLAUDE.md contract, AGENTS.md alias, ARCHITECTURE.md, docs structure, project agents, project skills, structured templates, language-matched validators, CI snippets, and Git hook templates. Use when setting up or refreshing a repository harness, adding target-owned Claude agents or skills to a repo, or wiring validation commands for Gradle, Maven, uv, bun, or shell projects.
---

# Harness Install

Install or refresh target-owned repository harness files from this plugin. This plugin skill is the visible runtime surface for installation guidance and orchestration; files that live inside a target repository are packaged under `skills/harness-install/assets/` and become target-owned after copying.

## Ownership Boundary

- This skill owns installation guidance and orchestration.
- Target repository agents, project skills, docs, validators, CI snippets, and hook scaffolds come from `skills/harness-install/assets/`.
- Plugin-root structural agents are not installable target assets.

## First Safe Checks

1. Confirm the target repository working tree is clean before running the installer. Run `git status --short` in the target; if the output is non-empty, stop and ask the user to commit or stash first. The installer writes new files (and with `--force` overwrites existing ones), and any rollback or recovery action against the working tree afterwards will be indistinguishable from unrelated in-progress work. Commit-first keeps the harness change set isolated and reversible.
2. Read the target repository root files if present: `AGENTS.md`, `CLAUDE.md`, `ARCHITECTURE.md`, and `docs/README.md`.
3. Determine the target stack from explicit user choice. The installer no longer auto-detects; confirm the stack with the user (inspect manifests such as `pom.xml`, `build.gradle*`, `pyproject.toml`/`uv.lock`, `package.json`/`bun.lock`, or `Makefile`/`*.sh`) before passing `--mode`.
4. Identify the active CI host. Use `git remote -v` to confirm whether the project ships through GitHub, GitLab, both, or neither, so unused CI files can be removed as a post-install step.
5. Git hooks are managed by ecosystem-standard tools: Husky for Bun, pre-commit framework for uv, and `core.hooksPath` for Gradle, Maven, and Shell. Each stack ships static hook files in its asset tree (`.husky/` for Bun, `.pre-commit-config.yaml` for uv, `.githooks/` for Gradle/Maven/Shell). The installer copies these as regular stack assets and activates hooks as a post-install step: `git config core.hooksPath .githooks/` for Gradle/Maven/Shell, `uv run pre-commit install` for uv, and `bun install` triggers Husky via the `prepare` script for Bun.
6. Use `--force` only when the user explicitly wants existing target harness files replaced.
7. Keep plugin files separate from target files: edit this plugin only when improving the installer; edit target `.claude/**` files only after installation in the target repository.

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
    "${CLAUDE_PLUGIN_ROOT:-/path/to/sinon/plugins/harness}/skills/harness-install/scripts/install-harness.py" --target "$PWD" --mode gradle --ci-host github
    ```

    ```sh
    uv run "${CLAUDE_PLUGIN_ROOT:-/path/to/sinon/plugins/harness}/skills/harness-install/scripts/install-harness.py" --target "$PWD" --mode gradle --ci-host github
    ```

3. Run the selected-mode validation command rendered into common assets (shown as `{{validation_command}}`) before reporting completion.

4. Inspect changed files and distinguish kept files from written files in the final report.

5. Report hook behavior, validation status, and any target-owned files the user must fill with project truth.

## Decisions

| Situation | Action |
| --- | --- |
| Target has no obvious stack files | Ask for `gradle`, `maven`, `uv`, `bun`, or `shell`; do not guess. |
| Existing harness files are present | Preserve them unless `--force` was requested. |
| Existing Git hook is present | Each stack uses ecosystem-standard hook management (Husky, pre-commit, or `core.hooksPath`). The installer activates hooks as a post-install step. Request explicit approval before changing active hooks directly. |
| Target uses GitHub CI | Pass `--ci-host github` to write only `.github/workflows/<tool>.yaml`. |
| Target uses GitLab CI | Pass `--ci-host gitlab` to write only `.gitlab-ci.yml`. |
| Target mirrors to GitHub and GitLab | Pass `--ci-host both` to write both CI files. The generated `pre-push` final check command must match both scripts. |
| Target does not run CI at all | Pass `--ci-host none` to skip CI file creation. |
| No CI host is specified | Request an explicit choice: GitHub, GitLab, both, or none. Pass `--ci-host <github\|gitlab\|both\|none>` to the installer. |
| Complex Gradle settings already exist | Review `settings.gradle(.kts)` after installer wiring; composite build or plugin-management blocks may need manual adjustment. |
| Installed placeholders are still generic | Treat installation as incomplete readiness and tell the user which docs need target content. |

## Invariants

- The installed harness is target-owned after copying.
- Git hooks use ecosystem-standard tools: Husky for Bun (`.husky/`), pre-commit framework for uv (`.pre-commit-config.yaml`), and `core.hooksPath` pointing to `.githooks/` for Gradle, Maven, and Shell. The installer activates hooks as a post-install step for Gradle, Maven, Shell, and uv; Bun hooks activate via `bun install` through the Husky `prepare` script.
- `pre-commit` hooks run the stack lint check (including markdownlint). `pre-push` hooks run the full validation including tests and broader checks where applicable (`./gradlew check`, `./mvnw verify`, `bun test`).
- Fresh installs use `CLAUDE.md` as the primary target repository harness contract and `AGENTS.md` as its symlink alias.
- Refreshes of existing AGENTS-only repositories may preserve `AGENTS.md` as the real file and add `CLAUDE.md` as the symlink alias; either orientation MUST resolve both filenames to the same document.
- `docs/generated/` is a generated-artifact location; it MUST NOT contain fake placeholder files.
- Plugin skills install, validate, and evolve the harness package; installed target skills and agents guide day-to-day work inside the target repository.
- Stack-specific check and fix commands run native ecosystem tools against Git-tracked files: Gradle `./gradlew ktlintCheck` and `./gradlew ktlintFormat`; Maven Checkstyle plus Spotless with `git ls-files` and `spotlessFiles`; uv `uv run scripts/check.py` and `uv run scripts/fix.py`; Bun `bun run check` and `bun run fix`; shell `sh scripts/check.sh` and `sh scripts/fix.sh`.

## Pitfalls

- Do not run the installer on a dirty working tree. Require the target repository to be committed or stashed first; `--force` overwrites tracked files, and any post-install rollback against an unclean tree will entangle harness changes with unrelated edits.
- Do not edit `scripts/install-harness.py` during ordinary installation.
- Do not claim harness-only readiness when product specs, architecture decisions, acceptance criteria, or generated artifacts are still placeholders.
- Do not force-replace target-owned hook files with `--force` without explicit approval for that repository.
- Do not treat seed references as universal target truth; they are replaceable starting points.
- Do not make the plugin responsible for runtime services, issue queues, workspace management, or team generation.

## Output Contract

Report these fields:

- `mode`: explicit stack mode (`--mode` flag).
- `installer command`: the command that ran.
- `files`: written, kept, and skipped file groups.
- `hooks`: ecosystem tool activation status for the selected stack (Husky, pre-commit, or `core.hooksPath`).
- `ci`: which CI files were rendered (GitHub Actions, GitLab CI, both, or none per --ci-host flag) and the active CI host detected from the target remote.
- `validation`: command run and result.
- `target follow-up`: placeholders, seed references, or unused CI files that require project-specific action.
