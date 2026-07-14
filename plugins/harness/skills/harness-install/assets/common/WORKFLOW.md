# Workflow

`WORKFLOW.md` defines the target repository's operational contract.

## Root Authority

The user-facing root session owns decomposition, integration, completion, and publication decisions.
The active host supplies native delegation capabilities.
Do not assume shipped agent profiles or vendor-specific model identifiers.

## Planning and Delegation

For non-trivial work, define success criteria, a brief plan, affected scope, and validation before implementation.
Use native delegation when decomposition materially improves the work.
Select capability tiers for the task:

- Use lightweight capability for inventory and mechanical checks.
- Use balanced capability for implementation and routine analysis.
- Use the strongest available capability for architecture, non-trivial debugging, and high-risk review.

When concurrent writers are useful, assign disjoint ownership and isolate their workspaces.
Wait for every dispatched worker before integrating results or drawing conclusions.
Preserve unrelated changes throughout the work.

## Validation and Completion

Run focused validation for changed behavior.
Run broader validation when integration risk, scope, or the change warrants it.
Record skipped checks and blockers with their reason.
The root session decides whether evidence satisfies the stated success criteria.

## Remote Effects and Autonomous Work

Remote mutation requires explicit user or repository authority.
Prepare local evidence and report a manual handoff when that authority is absent.
Use `autonomous-execution` only with explicit authorization for autonomous follow-through.
Use `issue-mining` for investigation and record preparation within its stated scope.
