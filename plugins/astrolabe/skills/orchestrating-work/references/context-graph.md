# Context Graph

A context graph carries decision-bearing steering and evidence references rather than a transcript.

## Task Payload

Every task payload includes `Goal`, `Background`, `PastFailures`, `Scope`, `NonGoals`, `Constraints`, `TargetPaths`, `Interfaces`, `AcceptanceCriteria`, `RequiredEvidence`, and `AuthorityBoundary`.

`PastFailures` records only relevant prior failures, unresolved findings, and checks that must not be repeated blindly.

`TargetPaths` names the owned mutable resources, while `AuthorityBoundary` states actions the worker may and may not perform.

## Result Payload

Every result includes `Status`, `Files`, `Signatures`, cumulative `Breaking*`, `Decisions`, `Summary`, `EvidenceRefs`, and `Blockers`.

Use explicit empty values when a field does not apply, and preserve the same field names across every activity.

`FindingsOrDispositions` is required for verifier results and records each finding with its disposition, reason, and evidence reference.

Cumulative `Breaking*` propagation is unchanged across dependent activities: pass the accumulated values forward, add newly discovered values, and never replace prior values with a local subset.

## Steering Edges

Use curated steering edges that carry the predecessor's goal-relevant decisions, constraints, signatures, findings, dispositions, and evidence references.

Do not pass source bodies, patches, logs, traces, screenshots, or raw command output across an edge.

Independent workers preserve predecessor isolation: each receives only its task payload and approved shared context, not another independent worker's private context or result.

Referenced-verifier steering carries the exact artifact paths, signatures, acceptance criteria, and prior decisions needed to verify the referenced activity, and no unrelated worker transcript.

## Ownership And Termination

Assign one owner to each mutable resource at a time.

Record ownership before editing, reject collisions before work begins, and serialize interface decisions that affect more than one owner.

Release the single mutable-resource ownership record at terminal completion, terminal failure, or an inspected recovery decision that safely abandons the activity.

A terminal result cannot leave mutable resources claimed or silently transfer ownership.
