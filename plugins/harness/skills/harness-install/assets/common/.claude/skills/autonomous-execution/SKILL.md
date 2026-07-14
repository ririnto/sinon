---
name: autonomous-execution
description: >-
  Run an explicitly authorized, bounded autonomous work loop. Use when a user asks for follow-through across multiple scoped work items with a clear stop condition.
---

# Autonomous Execution

Run one approved candidate at a time until the stop condition is met or work becomes blocked.

## First Safe Checks

1. Confirm explicit authorization for autonomous follow-through.
2. Record the approved scope and concrete stop condition.
3. Identify eligible candidates and select one.

## Procedure

1. Define acceptance criteria and an owning record for the selected candidate.
2. Execute and validate the candidate with the project's available commands and required local rules.
3. Stop for missing authority, policy, authentication, user intent, environment, or dependency. These failures do not consume a retry budget.
4. Record evidence, outcome, and the next decision for a failed or blocked candidate.
5. Continue only while another candidate remains within scope and the stop condition has not been met.

## Decisions

- Do not start work outside the approved scope.
- Do not perform remote mutation without explicit authority.
- Use native delegation only when it materially improves the work.
- Read `WORKFLOW.md` only when the target repository makes it relevant to the candidate.

## Output Contract

Return:

- `authorization and stop condition`.
- `cycles completed`.
- `current status`: success, blocked, or stopped by scope.
- `validation`.
- `review`: findings or residual risks.
- `completion`: completed records or reason not completed.
- `remaining candidates`.
- `blockers`: owner and next action.
