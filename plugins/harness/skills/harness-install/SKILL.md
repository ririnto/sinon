---
name: harness-install
description: >-
  Install packaged Harness assets in a target repository.
  Use when setting up target contracts, docs, project skills, stack validation, optional CI files, or inactive Git hook templates.
---

# Harness Install

Install one explicit stack mode into a target repository and use the canonical validation command printed by the installer.

## First Safe Checks

1. Run `git status --short` in the target. If it is dirty, stop unless the user explicitly accepts mixing installation with existing changes.
2. Confirm the target path, `--mode`, and required `--ci-host` value.
3. Inspect existing `AGENTS.md`, `CLAUDE.md`, `ARCHITECTURE.md`, and `WORKFLOW.md` before installing into an existing repository.
4. Obtain explicit approval before using `--force` or `--activate-hooks`.

## Procedure

1. Choose `gradle`, `maven`, `uv`, `bun`, or `shell`. The installer does not auto-detect.
2. Choose `github`, `gitlab`, `both`, or `none` for `--ci-host`. The flag controls CI assets only; every choice installs the same target `WORKFLOW.md`.
3. Preview the installation when target paths already exist:

    ```sh
    "${CLAUDE_PLUGIN_ROOT:-/path/to/sinon/plugins/harness}/skills/harness-install/scripts/install-harness.ts" \
      --target /path/to/target-repo --mode bun --ci-host github --preview
    ```

4. Run the full installer without `--preview`.
5. Run the printed canonical validation command from the target root.
6. Inspect written, kept, and conflicting files. Report target-specific placeholders separately from successful installation.

## Decisions

- Use `--force` only to replace approved conflicting content.
- Use `--only <path>` to install one selected target path.
- Use `--activate-hooks` only on a full install with approval. It validates copied `.githooks/pre-commit` and `.githooks/pre-push`, then sets local `core.hooksPath`.
- If the chosen CI host is `none`, no CI file is installed. When CI files exist, they MUST use the printed canonical check command.

## Output Contract

Report:

- `mode` and `ci host`.
- `installer command`.
- `files`: written, kept, and conflicts.
- `hooks`: inactive or explicitly activated.
- `validation`: command and result.
- `target follow-up`: placeholders, seed content, or CI policy decisions.

## Support Files

- Open `references/rule-interface.md` only when changing a stack validator, fix command, hook command, CI command, or native rule.
- Target-facing runtime assets live in `assets/`; edit them only for plugin-default changes, not one target's local evolution.
