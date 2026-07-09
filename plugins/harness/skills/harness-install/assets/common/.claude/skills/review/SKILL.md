---
name: review
description: >-
  Perform main-agent readiness review against repository contracts, docs, validation commands, and placeholder policy.
  Use this skill before merging changes that alter implementation, docs, generated artifacts, agents, skills, hooks, CI, or contract metadata.
---

# Review

Perform a main-agent readiness review against the current contract.
Report findings before summary.

## First Safe Checks

1. Read `ARCHITECTURE.md` and the relevant `docs/**` files.
2. Inspect the changed files and the relevant domain docs under `docs/**`.
3. Use the validation command supplied in the task prompt.
4. Separate product-code findings from contract findings.

## Workflow

1. Check whether the change follows repository instructions and architecture boundaries.
2. Check docs, specs, plans, generated artifacts, and validation surfaces affected by the change.
3. Verify placeholders are replaced with project-specific content before they are used as readiness evidence.
4. Verify generated artifacts document source command, source inputs, freshness, and regeneration trigger.
5. Run or require the validation command.
6. Report findings ordered by severity, with file references.

## Required Validation Review

Run the validation command before merge when a change alters:

- `CLAUDE.md` pointer integrity
- `AGENTS.md`
- `ARCHITECTURE.md`
- `WORKFLOW.md`
- `.gitignore`
- `.worktreeinclude`
- `.markdownlint-cli2.jsonc`
- `scripts/no-box-drawing.ts`
- `docs/**`
- `.claude/agents/`
- `.claude/skills/`
- `docs/templates/`
- `scripts/`
- stack hook assets and activation config
- CI jobs

For stack hook assets, check `.githooks/`, `.husky/`, `.pre-commit-config.yaml`, Gradle hook wiring, and Maven `core.hooksPath` when those files or settings exist.

## Review Focus

- Contract drift between `AGENTS.md`, `ARCHITECTURE.md`, docs, and stack hook assets.
- Workflow drift between supplied workflow policy, host CLI usage, and review evidence.
- Missing acceptance criteria, execution plans, or architecture updates.
- Required fake product content or obsolete references.
- Canonical check command drift across docs, CI, and the active `pre-commit` hook, or an active `pre-push` hook that is not a superset of that canonical check.
- Agents or skills with unclear trigger descriptions or unsupported scope.

## Output Contract

Report:

- `findings`: severity-ordered issues with file references.
- `validation`: command run or reason it was not run.
- `concessions`: valid minor improvements that should remain.
- `remaining risks`: unresolved placeholders or unverified surfaces.
