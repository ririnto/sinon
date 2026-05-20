---
name: harness-install
description: |-
  Install repository harness assets: AGENTS.md contract, ARCHITECTURE.md, docs structure, Claude entry point, project agents, project skills, structured templates, language-matched validators, CI snippets, and Git hook templates. Use this skill when setting up or refreshing a repository harness, adding Claude agents or skills to a repo, or wiring validation commands for Gradle, Maven, uv, or bun projects.
argument-hint: '[auto|gradle|maven|uv|bun] [--target DIR] [--hooks none|copy] [--force] [--no-ci]'
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

Install or refresh target-owned repository harness files from this plugin. The plugin skill installs the scaffold; after installation, target repositories own the copied contracts, docs, agents, skills, templates, validators, CI snippets, and generated hook templates.

## First Safe Checks

1. Read the target repository root files if present: `AGENTS.md`, `CLAUDE.md`, `ARCHITECTURE.md`, `.claude/harness/README.md`, and `.claude/harness/manifest.json`.
2. Detect the target stack from repository files, or use the explicit stack argument supplied by the user.
3. Identify the active CI host. Use `git remote -v` to confirm whether the project ships through GitHub, GitLab, both, or neither, so unused CI files can be removed as a post-install step.
4. Detect whether the target is a linked Git worktree. `.git` as a file means the user is inside a `git worktree add` working copy; the installer resolves the worktree hooks directory through the shared common dir.
5. Read the target's `core.hooksPath` Git config with `git config --get core.hooksPath`. When it already resolves to `.claude/harness/git-hooks`, prefer `--hooks none` so generated hook templates refresh in place; the installer refuses `--hooks copy` in that configuration.
6. Decide hook behavior before running the installer: `--hooks none` skips active hook installation, and `--hooks copy` writes the generated `pre-commit` and `pre-push` files into the worktree hooks directory only when they are absent unless `--force` is used.
7. Use `--force` only when the user explicitly wants existing target harness files replaced.
8. Keep plugin files separate from target files: edit this plugin only when improving the installer; edit target `.claude/**` files only after installation in the target repository.

## Workflow

1. Choose a mode.

    ```text
    auto
    gradle
    maven
    uv
    bun
    ```

2. Run the installer with the target repository as `--target`.

    ```sh
    sh "${CLAUDE_PLUGIN_ROOT:-/path/to/sinon/plugins/harness}/skills/harness-install/scripts/install-harness.sh" --target "$PWD" --mode auto --hooks none
    ```

3. Use explicit mode when auto-detection is ambiguous or when the user supplied a stack.

    ```sh
    sh "${CLAUDE_PLUGIN_ROOT:-/path/to/sinon/plugins/harness}/skills/harness-install/scripts/install-harness.sh" --target "$PWD" --mode gradle --hooks none
    ```

4. Run the validation command printed by the installer before reporting completion.
5. Inspect changed files and distinguish kept files from written files in the final report.
6. Report hook behavior, validation status, and any target-owned files the user must fill with project truth.

## Decisions

| Situation | Action |
| --- | --- |
| Target has no obvious stack files | Ask for `gradle`, `maven`, `uv`, or `bun`; do not guess. |
| Existing harness files are present | Preserve them unless `--force` was requested. |
| Existing Git hook is present | Prefer `--hooks none`; use `--hooks copy --force` only with explicit approval to replace active worktree pre-commit and pre-push hooks with installer-generated content. |
| Target repository forbids CI snippets | Pass `--no-ci` and report that CI templates were skipped. |
| Target uses only GitHub or only GitLab | Install both CI examples (default), then report the unused file (`.gitlab-ci.yml` or `.github/workflows/harness.yml`) as a target follow-up to delete. |
| Target mirrors to GitHub and GitLab | Keep both CI files and report that the generated `pre-push` final check command must match both scripts. |
| Linked Git worktree (`.git` is a file) | Document that `--hooks copy` installs into the shared worktree hooks directory resolved via `git rev-parse --git-common-dir`; do not assume `.git/hooks/` exists in the linked worktree itself. |
| Existing `core.hooksPath` points at `.claude/harness/git-hooks` | Use `--hooks none`; the installer refuses `--hooks copy` in this configuration because Git already activates the harness-tracked hooks. |
| Existing `core.hooksPath` points elsewhere | Use `--hooks none` and report that the target Git config bypasses the worktree hooks directory; `--hooks copy` will be rejected. |
| Complex Gradle settings already exist | Review `settings.gradle(.kts)` after installer wiring; composite build or plugin-management blocks may need manual adjustment. |
| Installed placeholders are still generic | Treat installation as incomplete readiness and tell the user which docs need target content. |

## Invariants

- The installed harness is target-owned after copying.
- The generated hook templates are target-owned: Gradle pre-commit runs `harnessValidate`, Gradle pre-push runs `check`, non-Gradle pre-commit checks harness-rule compliance only, non-Gradle pre-push runs selected validation, and custom templates are preserved unless `--force` was requested.
- `AGENTS.md` is the primary target repository harness contract.
- `CLAUDE.md` remains the Claude Code entry point and points back to `AGENTS.md`.
- `.claude/harness/manifest.json` is the installed harness inventory and contract.
- `docs/generated/` is a generated-artifact location; it MUST NOT require a fake `db-schema.md` file.
- Plugin skills install and validate the harness package; installed target skills guide day-to-day work inside the target repository.

## Pitfalls

- Do not edit `scripts/install-harness.sh` during ordinary installation.
- Do not claim harness-only readiness when product specs, architecture decisions, acceptance criteria, or generated artifacts are still placeholders.
- Do not activate or force-replace Git hooks without explicit approval for that local repository.
- Do not treat seed references as universal target truth; they are replaceable starting points.
- Do not make the plugin responsible for runtime services, issue queues, workspace management, or team generation.

## Output Contract

Report these fields:

- `mode`: detected or explicit stack mode.
- `installer command`: the command that ran.
- `files`: written, kept, and skipped file groups.
- `hooks`: `none` or `copy`; the resolved worktree hooks directory used for copy mode; whether pre-commit and pre-push files were written, kept, refreshed, or force-replaced.
- `ci`: which CI files were rendered (`.github/workflows/harness.yml`, `.gitlab-ci.yml`, both, or none) and the active CI host detected from the target remote.
- `worktree`: `primary` or `linked`, plus the active hooks directory when `--hooks copy` ran.
- `validation`: command run and result.
- `target follow-up`: placeholders, seed references, or unused CI files that require project-specific action.
