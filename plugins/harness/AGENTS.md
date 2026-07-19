# Repository Guidelines

Harness contains publishable plugin source and packaged assets for installing into target repositories.

## Source and Target Boundary

`skills/harness-install/` owns installer behavior and target-owned assets.
Keep plugin source, installer code, and packaged target content reproducible and package-local.
Do not mirror target-owned files into the marketplace root.
Use `assets/common/WORKFLOW.md` when a change affects target delegation or publication lifecycle.

## Installer Contract

Preserve an existing target `AGENTS.md`.
Only `--force` may replace target content that differs from the packaged source.
Run the copied-plugin smoke and installer safety tests after installer or asset changes.

## Asset Safety

Review installer path handling, hooks, command execution, and copied configuration for escape, ownership, credential, and publication risks.
