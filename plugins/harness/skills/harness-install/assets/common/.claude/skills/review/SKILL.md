---
name: review
description: >-
  Review target repository changes against installed repository contracts, docs, validation commands, and placeholder policy. Use this skill before merging changes that alter implementation, docs, generated artifacts, agents, skills, hooks, CI, or contract metadata.
---

# Review

Review changes in this target repository against the installed contract. Findings come first; summaries are secondary.

## First Safe Checks

1. Read `CLAUDE.md`, `ARCHITECTURE.md`, and `docs/README.md`.
2. Inspect the changed files and the relevant domain docs under `docs/**`.
3. Identify the stack-specific validation command from `docs/README.md`.
4. Separate product-code findings from contract findings.

## Workflow

1. Check whether the change follows repository instructions and architecture boundaries.
2. Check docs, specs, plans, generated artifacts, and validation surfaces affected by the change.
3. Verify placeholders are replaced with target-specific content before they are used as readiness evidence.
4. Verify generated artifacts document source command, source inputs, freshness, and regeneration trigger.
5. Run or require the stack-specific validation command.
6. Report findings ordered by severity, with file references.

## Review Focus

- Contract drift between `AGENTS.md`, `CLAUDE.md`, `ARCHITECTURE.md`, docs, and `docs/git-hooks/**`.
- Missing acceptance criteria, execution plans, or architecture updates.
- Required fake product content or obsolete seed references.
- Inconsistent final check commands across docs, pre-push, and CI, or pre-commit violating its stack-specific hook contract.
- Target agents or skills with unclear trigger descriptions or unsupported scope.

## Output Contract

Report:

- `findings`: severity-ordered issues with file references.
- `validation`: command run or reason it was not run.
- `concessions`: valid minor improvements that should remain.
- `remaining risks`: unresolved placeholders or unverified surfaces.
