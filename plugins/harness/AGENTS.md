# Repository Guidelines

These rules apply to `plugins/harness/`.
These rules override broader plugin guidance.
Harness contains plugin source, while target-owned files belong under `skills/harness-install/assets/`.

## Project Structure

`skills/harness-install/` owns packaged target assets and installer behavior.
Open `assets/common/WORKFLOW.md` when changing target delegation or publication lifecycle.

## Build, Test, and Development Commands

Run the copied-plugin smoke and installer safety tests after installer or asset changes.

## Coding Style and Testing

Keep installer-owned content reproducible and package-local.
Preserve an existing target `AGENTS.md`.
Only `--force` may replace target content that differs from the packaged source.

## Security and Configuration

Review installer path handling, hooks, command execution, and copied configuration for escape, ownership, credential, and publication risks.

## Scope and Precedence

Open the affected install or evolution skill before changing its runtime.
`assets/common/AGENTS.md` controls installed targets.
Do not mirror target-owned files into the marketplace root.
