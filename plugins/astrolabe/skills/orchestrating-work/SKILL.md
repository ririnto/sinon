---
name: orchestrating-work
description: Use when a task needs delegation, dependency-aware work selection, shared-resource ownership, interruption recovery, result reconciliation, or completion decisions.
---

# Orchestrating Work

## Own the Objective

The main agent owns the user objective, authority boundary, work graph, delegation decisions, integration, verification, and user-facing result.

Record the goal, scope, non-goals, constraints, target paths, interfaces, acceptance criteria, required evidence, and authority boundary before dispatching work.

Keep direct main-agent work to narrow reads of known files, mechanical integration, final Git operations, and final validation only when no useful delegation lane exists.

Delegate substantive exploration, analysis, editing, and validation when a scoped worker can improve evidence or throughput without overlapping another owner.

## Choose the Work Graph

Identify dependencies and choose the smallest graph that preserves predecessor decisions, resource ownership, and verifier inputs.

Give each activity a concrete name such as `inspect-hook-contracts`, `implement-lifecycle-recovery`, `verify-completion-checks`, or `reconcile-findings`.

Dispatch independent activities in the background and dispatch follow-ups in the background after their prerequisites are verified.

Use a planner only when a real design dependency needs explicit decomposition.

Do not dispatch overlapping writers to one mutable resource without a serialized ownership decision.

## Select Capability and Effort

Name a provider-neutral capability tier and effort for each activity.

Choose the least-cost sufficient tier and escalate only when evidence shows ambiguity, a failed check, a missing capability, or a dependency that the current selection cannot resolve.

Record the escalation trigger and evidence without naming providers or implementation backends.

## Dispatch With Bounded Context

Give each worker a self-contained payload with `Goal`, `Background`, `PastFailures`, `Scope`, `NonGoals`, `Constraints`, `TargetPaths`, `Interfaces`, `AcceptanceCriteria`, `RequiredEvidence`, and `AuthorityBoundary`.

Include the activity name, predecessor decisions, mutable resources, collision rules, and terminal release requirement when they affect safe execution.

Keep raw source, patches, logs, traces, screenshots, and command output at the producing node.

Pass only goal-relevant decisions, constraints, signatures, findings, dispositions, and evidence references across steering edges.

Preserve predecessor isolation for independent workers and give a verifier exact artifact paths, signatures, acceptance criteria, and prior decisions.

Assign one owner to each mutable resource, record ownership before editing, serialize cross-owner interface decisions, and release ownership at terminal completion or blockage.

## Reconcile Evidence and Issues

Normalize every result against the current artifacts before making a dependent decision.

Treat a worker's green check as a claim, inspect the current artifact, and rerun the final commands against the final inputs before declaring completion.

Treat missing, stale, failed, or inconclusive evidence as `UNKNOWN` until a current check resolves it.

Record every plausible in-scope issue until it has a disposition of `CONFIRMED`, `REJECTED`, `DUPLICATE`, or `OUT_OF_SCOPE`, with a reason and evidence reference.

An assessment-only activity may inspect and report tracker state, but it must not create, update, close, reopen, or otherwise mutate tracker records.

Repair only confirmed in-scope blockers, then rerun affected checks and final checks after every repair or changed input.

## Complete and Publish

Return `COMPLETED` only when the acceptance criteria and current evidence agree.

Return `Status`, `Files`, `Signatures`, cumulative `Breaking*`, `Decisions`, `Summary`, `EvidenceRefs`, and `Blockers`, and preserve every issue disposition through reconciliation.

Push a normal verified commit without asking for confirmation when the repository workflow authorizes publication.

Force-pushes and history rewrites require explicit authorization, even when ordinary publication is authorized.

Report the commit and verification evidence after an authorized push.

## Recover Only When Triggered

When work is interrupted, continuation is requested, a result is missing, a collision is reported, or a check becomes stale, inspect current artifacts, uncommitted changes, worker status, ownership records, pending checks, and the latest usable evidence before acting.

Classify the state as complete, partially applied, blocked, failed, collided, or unknown and attach evidence references.

Open [references/recovering-work.md](references/recovering-work.md) only for those recovery conditions.

Open [references/context-graph.md](references/context-graph.md) when task fields, steering edges, predecessor isolation, verifier inputs, or mutable-resource ownership need detailed rules.

Do not perform destructive checkout, history rewriting, publication, or authority expansion during recovery without the required authorization.
