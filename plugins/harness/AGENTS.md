# Repository Guidelines

This file applies to `plugins/harness/`. It overrides broader plugin guidance. Harness is plugin source, not an installed target; target-owned files stay under `skills/harness-install/assets/`.

## Project Structure

`skills/harness-install/` owns packaged target assets and installer behavior. `skills/harness-validate/` owns installed-record validation. Open the packaged `assets/common/WORKFLOW.md` for target delegation and publication lifecycle.

## Build, Test, and Development Commands

Run `plugins/harness/scripts/plugin-self-check.ts`, `claude plugin validate plugins/harness`, and `bun run check` for runtime, installer, validator, workflow, or asset changes. Regenerate the checked-in asset manifest after intentional asset inventory changes.

## Coding Style and Testing

Keep installer-owned content reproducible and package-local. Preserve an existing target `AGENTS.md`; only explicit tool-owned refresh may change proven managed content. Test installer behavior from plugin-only and nested-superproject fixtures.

## Commit and Publication

Keep target agents as leaves, use disjoint writer ownership, and return review fixes to the owning writer. The root session integrates and publishes.

## Security and Configuration

Review installer path handling, hooks, command execution, records, and copied configuration for escape, ownership, credential, and publication risks.

## Scope and Precedence

Open the affected install or validation skill before changing its runtime. `assets/common/AGENTS.md` controls installed targets; this file controls plugin source. Do not mirror target-owned files into the marketplace root.
