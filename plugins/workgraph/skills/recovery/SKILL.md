---
name: recovery
description: Use when a Workgraph node has interrupted or ambiguous identity, activity, ownership, or terminal state.
---

# Recovery

## Outcome

Restore the node or reach a verified terminal state without losing identity, ownership, authority, or unknown state.

## Establish Current State

Treat an interrupted stream, notification, transport, or continuation as unknown.
It proves neither failure nor completion.

Inspect available lifecycle metadata, run and node identity, activity, terminal-result presence, resource ownership, and current artifacts.
Do not start duplicate work while the prior node can still run or own resources.
Do not remove unrecorded state that can contain the only recoverable work.

Trust a result only when its run identity, node identity, declared dependencies, recorded terminal outcome, ownership release, and current artifacts agree.
Interpret the Workgraph outcome from terminal-result prose or decision-bearing node metadata. Do not assume a native task or workflow status uses the same vocabulary.
Keep `unknown` when the evidence cannot distinguish current work from stale state.
Retry only work known to be safe or idempotent.
Otherwise, inspect current state and return `unknown` or require manual reconciliation.

## Choose One Recovery Action

Resume the same node when its identity, context, and ownership remain valid.
Send changed steering and remaining acceptance criteria only.

Curate existing material when inspection can recover decision-bearing state.
Return verified status, recovered decisions and evidence, resource ownership, and blockers.
Keep raw working material local.

Replace the node when its context is unavailable, unsafe, corrupted, off scope, or detached from resource ownership.
Reassign ownership explicitly.
Build the replacement dispatch from the current source of truth and recovered decision-bearing state.

## Preserve Boundaries

Keep the original objective, node identity, dependencies, ownership, authority, and unknown state unless current evidence changes them.
Preserve the dispatched authority without expanding it.

Report verified completion, verified failure, a verified blocker, or the exact missing evidence.
Control-plane output does not prove task completion.
