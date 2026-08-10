---
name: orchestration
description: Use when this Skill is loaded for every Workgraph node dispatch and for dependency, ownership, concurrency, join, or terminal synthesis decisions.
---

# Orchestration

## Outcome

Build the smallest directed execution Graph that preserves authority, ownership, decision-bearing state, and terminal evidence.

## Select the Graph

Keep only exact-known narrow reads, simple control-plane tool calls, selective authoritative rereads,
repository-wide integration gates, authorized source-control integration, and terminal reporting
in the Main Agent.
Every implementation or task-side mutation uses at least one node, including small changes.
Use parallel Main Agent tool calls when its allowed work is independent.
Parallel calls alone do not justify multiple nodes.

Send broad, uncertain, multi-file, multi-source, or library discovery first to a haiku scout.
Use a scout Graph when discovery lanes are independent.
Open a Graph for every node dispatch.
Dense coding dependencies can favor one implementation node.

Use one node when one isolated lane is enough.
Assign validation to the owner of each changed input.
Create another node only when it owns distinct evidence or an independent acceptance decision.

Give each node an independently testable outcome and one bounded ownership lane.
Select model aliases in dispatch steering: haiku for exploration, research, simple bounded implementation,
or bounded review; sonnet for complex or multi-file implementation, substantial lane planning,
or comprehensive review; opus for architecture and other high-intelligence decisions, never implementation;
fable only when the user or dispatch explicitly requests it.
Never use model IDs, automatic escalation chains, or numeric routing thresholds.

## Construct the Graph

Before the first node dispatch, materialize the smallest inspectable execution Graph in concise prose or a native task/Workflow surface. A one-node Graph contains only applicable information; do not add empty scaffolding.
Give each node a stable identity, independently testable outcome, selected model alias, authority and owner, owned mutable resources, predecessor dependencies, acceptance evidence, and terminal condition.
Distinguish scheduling dependencies from decision-bearing state transfer on every edge. Treat a resource conflict as a scheduling dependency. For dynamic expansion, record each stable branch identity and its parent expansion, including an explicit empty expansion. Apply one dependency-readiness rule to ordinary dependents and every required fan-in input or branch: a node is ready only when every scheduling dependency is satisfied, required predecessor state is available, and no owned mutable resource conflicts.

| Outcome controlling the dependency | Effect |
| --- | --- |
| Predecessor `completed`, or compensation `completed` | satisfies the dependency |
| Predecessor `blocked`, or compensation `blocked` | blocks the dependent |
| Predecessor `failed` before compensation starts | fails the dependent unless an explicitly declared compensation is used |
| Predecessor `unknown`, or compensation `unknown` | keeps the dependent unknown and requires recovery |
| Compensation `failed` | fails the dependent |

After compensation starts, its outcome controls that dependency. The original failure does not override compensation. Record every propagated outcome in the Graph, even when the dependent was not dispatched.

For a dependent or fan-in, select one aggregate outcome from all required dependency outcomes, including compensation outcomes, using this order: `unknown` > `failed` > `blocked` > `completed`. Retain every contributing edge outcome as evidence, but use only the selected outcome to control downstream dependencies and closure; any selected outcome other than `completed` makes closure unsuccessful. A selected `unknown` outcome requires recovery to reconcile node state and release all owned mutable resources before closure.
Record an exit condition for every semantic loop.
Validate cycles and mutable-resource ownership before the first dispatch.

## Run the Graph

Direct each dependency from the predecessor that supplies required state to the dependent node.
Dispatch only nodes ready in the current Graph record, and dispatch them concurrently only when they have no mutable-resource conflict.
Treat a shared exclusive mutable resource as a scheduling dependency.
Serialize when a predecessor decision or exclusive mutable resource controls downstream work.
Assign each physical mutable resource to one active node at a time.
Do not integrate that resource until its owner returns a terminal result and releases it.

Collect every required terminal branch outcome before a join; do not silently truncate dynamic expansion or join inputs.
Use bounded expansion when the task defines a finite semantic set. Do not add universal numeric caps.
After any topology, ownership, or terminal-outcome change, update the Graph record before calculating the next ready set.

Run deterministic structural checks before qualitative review when both apply.
Synthesize terminal results in dependency order.
Resolve conflicts with the named acceptance criteria or synthesis owner.
Return a blocker when neither resolves the conflict.

Check node claims against the current source of truth before integration.
Close the Graph only when every required node has a terminal outcome, every result is reconciled,
all mutable resources are released, and stale or missing evidence is explicit.

## Load References at Their Triggers

Before dispatching a node or accepting its terminal result, read [payload contracts](references/payload-contracts.md).
