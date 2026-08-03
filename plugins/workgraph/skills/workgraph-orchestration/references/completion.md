# Completion and Issue Disposition

## Completion Gate

Declare completion only when every planned function is implemented and current final-state evidence satisfies the acceptance criteria.

Confirm that all required terminal results belong to the current run, mutable-resource ownership has ended, integrated changes exist in the intended target, and checks reflect the final inputs.

If inputs changed after a check began, rerun that check against the final inputs.

When validation cannot run, identify the missing evidence and use the best available substitute without turning uncertainty into a factual negative.

After a failed mutation, inspect partial state before retrying and retry only when the operation is idempotent or the inspected state makes it safe.

Use the smallest root-cause change that completes the work and do not add unrelated cleanup or speculative infrastructure while closing it.

## Findings

Preserve every plausible in-scope finding until triage assigns a disposition.

Verify a finding against the current source or artifact before reporting it as confirmed.

Use `CONFIRMED`, `REJECTED`, `DUPLICATE`, or `OUT_OF_SCOPE` with a concise reason and evidence reference.

Repair an in-scope blocker within the authorized implementation scope.

Move an out-of-scope issue to separate work rather than silently dropping or fixing it.

Workers propose issue dispositions in `Decisions` unless the task explicitly grants tracker ownership.

Assessment-only work reports evidence and dispositions without mutating a tracker.

Before any tracker write, identify the repository's owning tracker and native terminology from repository documentation, remote metadata, or project configuration.

Do not assume a tracker when available evidence does not identify one.
