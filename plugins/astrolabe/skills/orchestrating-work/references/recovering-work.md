# Recovering Work

Use this reference only when an activity is interrupted, a continuation is requested, a worker result is missing, a collision is reported, or a check becomes stale after a change.

## Inspect Before Acting

Inspect the current target files, uncommitted changes, ownership records, worker status, result payloads, pending checks, and the most recent usable evidence.

Do not infer completion from a stopped process, a continuation request, or a prior result whose inputs changed.

Classify the observed state as complete, partially applied, blocked, failed, collided, or unknown, and attach the evidence references.

## Recovery Choices

If the required artifact and current evidence are complete, run the final checks again and close the activity without duplicate work.

If an edit is partial but safely attributable to the owned activity, preserve unrelated changes, complete the smallest missing change, and rerun all affected checks.

If a result is missing or stale, inspect the artifacts and rerun the narrowest missing activity with a fresh payload and the same authority boundary.

If a mutable-resource collision exists, stop the losing activity, preserve both workers' evidence locally, and let the owner with the valid serialized decision continue.

If the activity is blocked by missing authority, unsafe side effects, or unresolved interface decisions, release its ownership and report `BLOCKED` with the exact evidence needed to continue.

If recovery would require destructive checkout, history rewriting, publication, or an authority expansion, stop and report the boundary instead of performing it.

## Continuation Checks

A follow-up starts with inspection, not with assumptions about prior context.

Reconfirm the goal, current files, unresolved findings, acceptance criteria, required evidence, and ownership before dispatching any follow-up activity.

A follow-up activity is independent only when it has no unverified dependency on the interrupted activity's private state.

After recovery edits, rerun the affected checks and then rerun the final checks against the final inputs.
