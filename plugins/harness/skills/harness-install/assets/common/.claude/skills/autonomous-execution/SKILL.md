---
name: autonomous-execution
description: >-
  Orchestrate repeated autonomous work cycles across discovery, planning, implementation, validation, review, and publication.
  Use when the user asks the agent to keep working beyond one scoped item, continue autonomously, find the next improvement candidates, or run an execution loop until a stop condition is met.
---

# Autonomous Execution

Run repeated scoped work cycles until the user-approved stop condition is met or a named owner decision is required.
Use this skill only after the user explicitly authorizes autonomous follow-through beyond one scoped work item.

## Operating Rules

- Use `WORKFLOW.md` for branch and worktree policy, subagent selection, model class, evidence, and publication or completion.
- Write down the user-approved scope and stop condition before the first cycle.
- Use one scoped outcome per cycle.
- Give each candidate an owning record, acceptance criteria, failure limit, validation command, review checklist, and publication or completion target before implementation.
- Process non-overlapping candidates in separate worktrees when their files, contracts, validation surfaces, and publication or completion targets do not conflict.
- Use `issue-mining` for issue investigation and registration.
- Use `review` for readiness review.
- Use `implementation` and `review` agents only through the orchestrator's subagent policy.

## First Safe Checks

1. Confirm the user request authorizes autonomous follow-through.
2. Use `WORKFLOW.md` to confirm the host or local review flow.
3. Confirm the host flow: GitHub, GitLab, or local review.
4. Write down scope, stop condition, validation command, review target, and publication or completion target.
5. Identify candidate sources: user request, issue mining, validation failures, review findings, or repository scan.

## Procedure

1. Select the next improvement candidate inside the user-approved scope.
2. Define impact, acceptance criteria, changed files, validation, review target, and publication or completion target.
3. Split candidates into separate worktrees only when their scopes do not overlap.
4. Execute one scoped work cycle per candidate through `WORKFLOW.md`.
5. Run the selected validation command from `WORKFLOW.md`.
6. Run the repository readiness review.
7. Fix blocking failures within the failure limit.
8. Publish or complete the owning record through `WORKFLOW.md` after validation, review, and evidence are complete.
9. Continue to the next candidate only while it remains inside the user-approved scope and stop condition.

## Exit Conditions

Stop when either outcome applies.

Success:

- The user-approved autonomous scope is complete.
- Validation passes for each completed candidate.
- No unresolved high or critical findings remain.
- Evidence is complete for the owning record.

Blocked:

- A human owner must decide business intent or platform policy.
- A repeated blocker has been retried twice without convergence.
- Remaining blockers name an owner and next action.

## Output Contract

Return:

- `cycles completed`: completed candidates and owning records
- `current status`: success, blocked, or stopped by scope
- `validation`: commands run and results
- `review`: findings, approvals, or residual risks
- `publication/completion`: host records, local records, or reason not completed
- `remaining candidates`: candidates not started
- `blockers`: owner and next action
