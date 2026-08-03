# Payload Contracts

## Context Partition

Partition each node's context into decision-bearing steering and node-local working I/O.

Steering contains goals, decisions, constraints, paths, interfaces, signatures, compact dependency relations, acceptance criteria, evidence references, blockers, and authority.

Working I/O contains source bodies, patches, raw logs, raw test streams, screenshots, traces, and transcripts.

Keep the two sets disjoint.

## Edge Rule

Every delegated edge carries a curated subset of steering and excludes working I/O.

The Main Agent receives node results rather than node-local working I/O.

Code bodies must not appear in any result field.

A downstream node may read the source or evidence within its own authority, where it becomes that node's local working I/O.

## Composed Steering History

A downstream task receives only the curated steering of its declared predecessors.

Do not append the full sequence of predecessor results or reconstruct parent, sibling, or worker transcripts to simulate history.

Compose the steering still needed downstream into one curated predecessor payload.

Preserve cumulative `Breaking` entries losslessly across every declared dependency edge.

Compress or omit other predecessor steering when no downstream decision depends on it.

An independent node receives no predecessor result.

## Worker Task

Use every field and provide an explicit empty value when it does not apply.

```json
{
  "Goal": "",
  "Background": "",
  "PastFailures": [],
  "Scope": [],
  "NonGoals": [],
  "Constraints": [],
  "TargetPaths": [],
  "Interfaces": [],
  "AcceptanceCriteria": [],
  "RequiredEvidence": [],
  "AuthorityBoundary": ""
}
```

The task must be self-contained and must not depend on unstated parent, sibling, or session context.

## Verifier or Curator Task

```json
{
  "ObjectiveOrClaim": "",
  "Scope": [],
  "AcceptanceCriteria": [],
  "EvidenceRefs": [],
  "RequiredChecks": [],
  "AuthorityBoundary": ""
}
```

## Worker Result

```json
{
  "Status": "COMPLETED | BLOCKED | FAILED | UNKNOWN",
  "Files": [],
  "Signatures": [],
  "Breaking": [],
  "Decisions": [],
  "Summary": "",
  "EvidenceRefs": [],
  "Blockers": []
}
```

`Breaking` must preserve prior entries without renaming or compression loss.

## Verifier or Curator Result

```json
{
  "Status": "COMPLETED | BLOCKED | FAILED | UNKNOWN",
  "Files": [],
  "Signatures": [],
  "Breaking": [],
  "FindingsOrDispositions": [],
  "Decisions": [],
  "Summary": "",
  "EvidenceRefs": [],
  "Blockers": []
}
```

## Ownership

One node owns each physical mutable resource at a time.

A terminal result releases every mutable resource owned by that node.

Continuation or replacement requires an explicit ownership reassignment.

Delegation divides existing authority and never creates new authority for destructive actions, material cost, shared or external writes, third-party communication, or scope expansion.
