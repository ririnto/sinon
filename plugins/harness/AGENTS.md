# Repository Guidelines

These rules apply to `plugins/harness/`.
These rules override broader plugin guidance.
Harness contains plugin source, while target-owned files belong under `skills/harness-install/assets/`.

## Project Structure

`skills/harness-install/` owns packaged target assets and installer behavior.
`skills/harness-validate/` owns installed-record validation.
Open `assets/common/WORKFLOW.md` when changing target delegation or publication lifecycle.

## Build, Test, and Development Commands

Run `claude plugin validate plugins/harness` and the relevant repository checks after runtime, installer, validator, workflow, or asset changes.
Run `bun scripts/generate-harness-asset-manifest.ts` after intentional asset inventory changes.

## Coding Style and Testing

Keep installer-owned content reproducible and package-local.
Preserve an existing target `AGENTS.md`.
Only explicit tool-owned refresh may change proven managed content.
Use the repository-side copied-plugin smoke and focused installer safety tests.

## Commit and Publication

Keep delegated agents as bounded leaves, use capability-based delegation from the active host, assign disjoint writer ownership, and return review fixes to the owning writer.
The root session integrates and publishes.

## Security and Configuration

Review installer path handling, hooks, command execution, records, and copied configuration for escape, ownership, credential, and publication risks.

## Scope and Precedence

Open the affected install or validation skill before changing its runtime.
`assets/common/AGENTS.md` controls installed targets.
This file controls plugin source.
Do not mirror target-owned files into the marketplace root.
