---
name: orchestration
description: Use when sizeable work needs isolated node ownership, specialized capability, real parallel execution, dependency coordination, or multi-node result synthesis.
---

# Orchestration

## Outcome

Build the smallest directed execution Graph that preserves authority, ownership, decision-bearing state, and terminal evidence.

## Select the Graph

Keep small tasks and narrow tool sequences in the Main Agent.
Use parallel tool calls there when independent reads are needed together.
Parallel calls alone do not justify nodes.

Open a Graph only when at least one sizeable lane benefits from:

- independent ownership;
- isolated context;
- specialized capability;
- real parallel execution.

Use one node when one isolated lane is enough.
Assign validation to the owner of each changed input.
Create another node only when it owns distinct evidence or an independent acceptance decision.

Give each node an independently testable outcome and one bounded ownership lane.
Use the current model and effort unless representative evaluation supports a change for that task type.

## Run the Graph

Require declared dependencies to be acyclic before dispatch.
Restructure a cycle, or return a blocker if no valid dependency Graph preserves the required semantics.
Direct each dependency from the predecessor that supplies required state to the dependent node.
Dispatch ready nodes concurrently when they have no unmet dependencies and no mutable-resource conflict.
Serialize only when a predecessor decision or exclusive mutable resource controls downstream work.
Assign each physical mutable resource to one active node at a time.
Do not integrate that resource until its owner returns a terminal result and releases it.

Synthesize terminal results in dependency order.
Resolve conflicts with the named acceptance criteria or synthesis owner.
Return a blocker when neither resolves the conflict.

Check node claims against the current source of truth before integration.
Close the Graph only when every required node has a terminal outcome, every result is reconciled, all mutable resources are released, and stale or missing evidence is explicit.

## Load References at Their Triggers

Before dispatching a node or accepting its terminal result, read [payload contracts](references/payload-contracts.md).
