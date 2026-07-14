# Workflow

`WORKFLOW.md` defines the target repository's delivery lifecycle.

## Root Authority

The user-facing root session MUST own scope, decomposition, integration, completion, and publication decisions.
The workflow MUST NOT require shipped agent profiles, a specific CLI, or vendor-specific model identifiers.

## Planning and Routing

For non-trivial work, the root MUST state the affected scope, material assumptions, verifiable success criteria, and validation approach before implementation.
It MUST ask for direction before choosing between outcomes that would change the requested result.

Model capability and reasoning effort are separate choices.
The root SHOULD choose the lowest capability and effort that can satisfy the success criteria:

- lightweight capability for bounded inventory, extraction, and mechanical work;
- balanced capability for routine implementation, analysis, and tool use;
- the strongest available capability for architecture, non-trivial debugging, ambiguous decomposition, and high-risk review.

The root SHOULD use deeper reasoning for one hard, indivisible task.
It SHOULD use proactive delegation only for meaningful independent parts.
Most tasks require neither deeper reasoning nor proactive delegation, and delegation MUST NOT become mandatory.

## Delegation and Context

The root SHOULD delegate only when independent decomposition produces a clear gain in speed, quality, coverage, or context control.
It SHOULD favor parallel workers for bounded exploration, focused validation, log analysis, and independent review.
It SHOULD use concurrent writers only when their benefit exceeds coordination cost.

Each worker MUST receive a self-contained task with:

- a bounded goal, relevant context, constraints, and completion criteria;
- expected evidence and validation;
- write authority and exact ownership when writing is allowed.

Workers MUST preserve unrelated changes and report blockers.
They SHOULD return concise findings and evidence instead of unfiltered logs.
The root SHOULD keep its context focused on requirements, decisions, integration, and the final result.

Direct workers MUST remain bounded leaves.

The root MUST verify required authority before dispatch and MUST NOT assume a worker can obtain new approval in a non-interactive run.

## Ownership and Fan-In

Concurrent writers MUST receive disjoint ownership and use isolated workspaces when the host supports them.
The root MUST serialize writes when the host cannot provide isolation.
Review fixes SHOULD return to the owning writer when practical.

The root MUST wait for every dispatched worker before integrating results or drawing dependent conclusions.
A failed, missing, or blocked worker MUST block fan-in.
The root MUST resolve the blocker before integrating results or drawing conclusions.

## Validation and Completion

The root MUST run focused validation for changed behavior.
It SHOULD add or update a focused regression when the repository has an appropriate test surface for the behavior.
It SHOULD run broader checks only when repository rules, scope, or integration risk warrants them.

Before completion, the root MUST inspect the integrated diff and review it for defects, regressions, unsafe effects, and missing documentation.
High-risk work SHOULD receive independent review.
The root MUST resolve review findings and rerun affected checks before handoff.
It MUST report validation results, skipped checks, and blockers with their reasons.
The root decides whether the evidence satisfies the stated success criteria.

## Remote Effects and Autonomous Work

Remote mutation, publication, deployment, and external messaging require explicit user or repository authority.
Without that authority, the root MUST prepare local evidence and report the required handoff.

Autonomous follow-through applies only to the authorized scope.
It MUST NOT grant authority for unrelated changes, credentials, remote effects, or broader publication decisions.
Use `autonomous-execution` and `issue-mining` only within each skill's stated scope and authority.
