---
name: orchestration
description: Plan and run a bounded execution Graph with isolated node context and deterministic synthesis. Use when work needs delegation, parallel lanes, recovery, or multi-node result integration.
---

# Orchestration

## Outcome

Use the smallest directed execution Graph that preserves correctness, authority, ownership, evidence, and recoverable state.

## Workflow

1. Define the objective, scope, non-goals, acceptance criteria, required evidence, and authority boundary.
2. When the host runtime exposes the Workflow Tool, use it as the execution mechanism for Graph-based work delegation.
3. Keep the work in the Main Agent when current evidence and a narrow tool sequence are sufficient.
4. Add a scout for broad or uncertain discovery, a worker for a sizeable owned lane, a planner for a real design dependency, or a verifier only when independent judgment changes confidence.
5. Define each node by an independently testable outcome, declared dependencies, mutable-resource ownership, and terminal evidence.
6. Dispatch dependency-independent nodes concurrently and serialize only design, interface, semantic, or non-isolatable state dependencies.
7. Keep working I/O local to the node and pass curated steering across every edge.
8. Synthesize results in declared lane or dependency-topological order rather than arrival order.
9. Resolve disagreement through acceptance criteria or the named synthesis owner and return a blocker when neither decides it.
10. Integrate only after worker ownership ends and verify claims against the current source of truth.

## Load Detailed Material Only at Its Trigger

- Before choosing Graph shape, concurrency, model capability, or effort, read [delegation selection](references/delegation-selection.md).
- Before dispatching or accepting a node result, read [payload contracts](references/payload-contracts.md).
- When a stream, notification, worker, or continuation state is interrupted or ambiguous, read [recovery](references/recovery.md).
- Before declaring completion or disposing discovered issues, read [completion](references/completion.md).

## Stop Condition

Stop when all required terminal results have been reconciled, mutable ownership has returned to the integration owner, final evidence matches the current artifacts, and residual blockers are explicit.
