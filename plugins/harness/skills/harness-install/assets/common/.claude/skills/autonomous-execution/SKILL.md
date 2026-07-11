---
name: autonomous-execution
description: >-
  Run authorized autonomous work cycles with explicit scope, stop conditions, and per-candidate retry limits.
  Use when the user asks for continued follow-through beyond one scoped item or requests a bounded execution loop.
---

# Autonomous Execution

Run one candidate at a time until the loop reaches the approved stop condition
or blocks.
Use this skill only after the user explicitly authorizes autonomous follow-through beyond one scoped work item.

## Operating Rules

- Record the user's authorization, approved scope, and stop condition before the first cycle.
- Process exactly one candidate per cycle.
- Use `WORKFLOW.md` for document-edit rules, routing, and delegated phase ownership.
- Track failure counts separately for each candidate.

## First Safe Checks

1. Confirm the user's authorization for autonomous follow-through.
2. Record the approved scope and concrete stop condition.
3. Identify eligible candidates from the approved sources.
4. Select one candidate and initialize its failure count at zero.

## Procedure

1. Select one candidate inside the approved scope and define its acceptance criteria and owning record.
2. Execute the candidate through `WORKFLOW.md`.
3. Stop immediately for an authority, policy, authentication, missing user intent, or unavailable environment or dependency failure.
   These terminal failures do not consume a retry budget.
4. Apply the failed-leaf recovery lifecycle to exploration, implementation, review, and validation failures.
   A failed leaf clears only with discarded disposition, changed remediation, and a passed replacement with a globally fresh identity.
5. Define `failedCycles` as the integer count of prior retryable failed cycles for that candidate.
   The value MUST be zero before the first failure or one before the second; negative, fractional, and larger values block the cycle.
6. A valid current retryable failure consumes one failure budget.
   Zero permits one retry; one exhausts the budget and stops without a third attempt.
7. Block autonomous execution for an invalid recovery or two failed cycles and report the evidence, owner, and next decision required.
8. Defer routing, isolation, review, validation, and completion authority to `WORKFLOW.md`.
9. Continue only while the next candidate remains inside the approved scope and stop condition.

## Exit Conditions

Stop when either outcome applies.

Success:

- The approved autonomous scope and stop condition are satisfied.
- Each completed candidate passes the `Canonical Workflow Completion Gate`.

Blocked:

- A human owner must decide business intent, scope, or platform policy.
- A candidate reaches two failed cycles.
- Remaining blockers name an owner and next action.

## Output Contract

Return:

- `authorization and stop condition`: explicit authorization, scope, and stop condition
- `cycles completed`: completed candidates and owning records
- `current status`: success, blocked, or stopped by scope
- `failure counts`: each attempted candidate and count
- `validation`: commands run and results
- `review`: findings, approvals, or residual risks
- `publication/completion`: host records, local records, or reason not completed
- `remaining candidates`: candidates not started
- `blockers`: owner and next action
