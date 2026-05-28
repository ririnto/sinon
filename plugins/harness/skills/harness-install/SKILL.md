---
name: harness-install
description: >-
  Install target-owned repository harness assets from the plugin asset package: CLAUDE.md contract, AGENTS.md alias, ARCHITECTURE.md, docs structure, project agents, project skills, structured templates, language-matched validators, CI snippets, and Git hook templates. Use when setting up or refreshing a repository harness, adding target-owned Claude agents or skills to a repo, or wiring validation commands for Gradle, Maven, uv, bun, or shell projects.
argument-hint: '--mode gradle|maven|uv|bun|shell [--target DIR] [--hooks none|copy|build-tool] [--force] [--no-ci]'
disable-model-invocation: true
allowed-tools:
  - Bash(sh */skills/harness-install/scripts/install-harness.sh *)
  - Bash(git *)
  - Bash(./gradlew *)
  - Bash(gradle *)
  - Bash(mvn *)
  - Bash(uv *)
  - Bash(bun *)
  - Read
  - Grep
  - Glob
  - LS
---

# Harness Install

Install or refresh target-owned repository harness files from this plugin. This plugin skill is the visible runtime surface for installation guidance and orchestration; files that live inside a target repository are packaged under `skills/harness-install/assets/` and become target-owned after copying.

## Ownership Boundary

- This skill owns installation guidance and orchestration.
- Target repository agents, project skills, docs, validators, CI snippets, and hook scaffolds come from `skills/harness-install/assets/`.
- Plugin-root structural agents are not installable target assets.

## First Safe Checks

1. Confirm the target repository working tree is clean before running the installer. Run `git status --short` in the target; if the output is non-empty, stop and ask the user to commit or stash first. The installer writes new files (and with `--force` overwrites existing ones), and any rollback or recovery action against the working tree afterwards will be indistinguishable from unrelated in-progress work. Commit-first keeps the harness change set isolated and reversible.
2. Read the target repository root files if present: `AGENTS.md`, `CLAUDE.md`, `ARCHITECTURE.md`, `docs/harness/README.md`, and `docs/harness/manifest.json`.
3. Determine the target stack from explicit user choice. The installer no longer auto-detects; confirm the stack with the user (inspect manifests such as `pom.xml`, `build.gradle*`, `pyproject.toml`/`uv.lock`, `package.json`/`bun.lock`, or `Makefile`/`*.sh`) before passing `--mode`.
4. Identify the active CI host. Use `git remote -v` to confirm whether the project ships through GitHub, GitLab, both, or neither, so unused CI files can be removed as a post-install step.
5. Detect whether the target is a linked Git worktree. `.git` as a file means the user is inside a `git worktree add` working copy; the installer resolves the worktree hooks directory through the shared common dir.
6. Read the target's `core.hooksPath` Git config with `git config --get core.hooksPath`. When it already resolves to `docs/harness/git-hooks`, prefer `--hooks none` so generated hook templates refresh in place; the installer refuses `--hooks copy` in that configuration.
7. Decide hook behavior before running the installer: `--hooks none` skips active hook installation, `--hooks copy` writes the generated `pre-commit` and `pre-push` files into the worktree hooks directory only when they are absent unless `--force` is used, and `--hooks build-tool` prints explicit build-tool activation commands so the user can run them manually. Build-tool mode is only available for Gradle and Maven.
8. Use `--force` only when the user explicitly wants existing target harness files replaced.
9. Keep plugin files separate from target files: edit this plugin only when improving the installer; edit target `.claude/**` files only after installation in the target repository.

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
    sh "${CLAUDE_PLUGIN_ROOT:-/path/to/sinon/plugins/harness}/skills/harness-install/scripts/install-harness.sh" --target "$PWD" --mode gradle --hooks none
    ```

3. Run the validation command printed by the installer before reporting completion.
4. Inspect changed files and distinguish kept files from written files in the final report.
5. Report hook behavior, validation status, and any target-owned files the user must fill with project truth.

## Decisions

| Situation | Action |
| --- | --- |
| Target has no obvious stack files | Ask for `gradle`, `maven`, `uv`, `bun`, or `shell`; do not guess. |
| Existing harness files are present | Preserve them unless `--force` was requested. |
| Existing Git hook is present | Prefer `--hooks none`; use `--hooks copy --force` only with explicit approval to replace active worktree pre-commit and pre-push hooks with installer-generated content. |
| Target repository forbids CI snippets | Pass `--no-ci` and report that CI templates were skipped. |
| Target uses only GitHub or only GitLab | Install both CI examples (default), then report the unused file (`.gitlab-ci.yml` or `.github/workflows/harness.yml`) as a target follow-up to delete. |
| Target mirrors to GitHub and GitLab | Keep both CI files and report that the generated `pre-push` final check command must match both scripts. |
| Linked Git worktree (`.git` is a file) | Document that `--hooks copy` installs into the shared worktree hooks directory resolved via `git rev-parse --git-common-dir`; do not assume `.git/hooks/` exists in the linked worktree itself. |
| Existing `core.hooksPath` points at `docs/harness/git-hooks` | Use `--hooks none`; the installer refuses `--hooks copy` in this configuration because Git already activates the harness-tracked hooks. |
| Existing `core.hooksPath` points elsewhere | Use `--hooks none` and report that the target Git config bypasses the worktree hooks directory; `--hooks copy` will be rejected. |
| Complex Gradle settings already exist | Review `settings.gradle(.kts)` after installer wiring; composite build or plugin-management blocks may need manual adjustment. |
| Installed placeholders are still generic | Treat installation as incomplete readiness and tell the user which docs need target content. |
| Installed placeholders are still generic | Treat installation as incomplete readiness and tell the user which docs need target content. |
| Gradle target wants build-tool hook activation | Use `--hooks build-tool`; the installer prints `./gradlew -Pharness.gitHooks=true help` (or system `gradle`) so the user can run it, then lets `settings.gradle.kts` apply `org.danilopianini.gradle-pre-commit-git-hooks` 2.1.17 and create hooks. |
| Maven target wants build-tool hook activation | Use `--hooks build-tool`; the installer prints `mvn -q -f harness-maven-plugin/pom.xml -Dharness.gitHooks=true generate-sources` for the user to run, which activates the `harness-git-hooks` profile to set `core.hooksPath` to `docs/harness/git-hooks` via `git-build-hook-maven-plugin` 3.6.0. |
| uv/bun/shell target requests `--hooks build-tool` | Reject with an error; use `--hooks none` or `--hooks copy` instead. |
| Existing `core.hooksPath` conflicts with build-tool | The installer rejects `--hooks build-tool` when `core.hooksPath` already points elsewhere to avoid stacking activation. |
| Gradle linked worktree with build-tool hooks | The Gradle `gradle-pre-commit-git-hooks` plugin creates hooks relative to the repository root directory; in linked worktrees the plugin may place hooks in the wrong `.git` directory. Prefer `--hooks copy` or manual `core.hooksPath` for linked worktree Gradle projects. |

## Invariants

- The installed harness is target-owned after copying.
- The generated hook templates are target-owned: Gradle pre-commit runs `harnessCheck`, Gradle pre-push runs `check`, non-Gradle pre-commit checks harness-rule compliance only, non-Gradle pre-push runs selected validation, and custom templates are preserved unless `--force` was requested.
- Fresh installs use `CLAUDE.md` as the primary target repository harness contract and `AGENTS.md` as its symlink alias.
- Refreshes of existing AGENTS-only repositories may preserve `AGENTS.md` as the real file and add `CLAUDE.md` as the symlink alias; either orientation MUST resolve both filenames to the same document.
- `docs/harness/manifest.json` is the installed harness inventory and contract.
- `docs/generated/` is a generated-artifact location; it MUST NOT contain fake placeholder files.
- Plugin skills install, validate, and evolve the harness package; installed target skills and agents guide day-to-day work inside the target repository.
- Bun and uv runtimes install `harness-check` and `harness-format`. Gradle and Maven expose `harnessFormat` / `format` for allowlisted safe fixes. `harnessFormat` applies only allowlisted safe fixes and is idempotent. The shell runtime installs `harness-check.sh` and `harness-format.sh`. Format commands run validation after applying fixes, print remaining findings, and fail when any remaining finding has `ERROR` severity.

## Pitfalls

- Do not run the installer on a dirty working tree. Require the target repository to be committed or stashed first; `--force` overwrites tracked files, `--hooks copy` writes outside version control, and any post-install rollback against an unclean tree will entangle harness changes with unrelated edits.
- Do not edit `scripts/install-harness.sh` during ordinary installation.
- Do not claim harness-only readiness when product specs, architecture decisions, acceptance criteria, or generated artifacts are still placeholders.
- Do not activate or force-replace Git hooks without explicit approval for that local repository.
- Do not treat seed references as universal target truth; they are replaceable starting points.
- Do not make the plugin responsible for runtime services, issue queues, workspace management, or team generation.

## Output Contract

Report these fields:

- `mode`: explicit stack mode (`--mode` flag).
- `installer command`: the command that ran.
- `files`: written, kept, and skipped file groups.
- `hooks`: `none`, `copy`, or `build-tool`; the resolved worktree hooks directory used for copy mode; whether pre-commit and pre-push files were written, kept, refreshed, or force-replaced; build-tool should report the exact command(s) printed for manual activation.
- `ci`: which CI files were rendered (`.github/workflows/harness.yml`, `.gitlab-ci.yml`, both, or none) and the active CI host detected from the target remote.
- `worktree`: `primary` or `linked`, plus the active hooks directory when `--hooks copy` ran.
- `validation`: command run and result.
- `target follow-up`: placeholders, seed references, or unused CI files that require project-specific action.
