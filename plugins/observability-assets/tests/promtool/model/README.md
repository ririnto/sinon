# Promtool lifecycle models

These bounded models describe asynchronous control flow in [the Promtool runner](../run.ts).
They check safety properties of the abstraction, not Node.js, network behavior, or the complete program.
The existing process tests exercise runtime behavior.

## Running TLC

From the repository root, run the checks with Java and a POSIX shell with `mktemp`.
The locked `tla-chaos-loop` dependency supplies TLC.
Each command creates and removes its own temporary metadata directory.

```sh
pnpm run test:promtool:models
pnpm run check:promtool-release
```

## Download lifecycle

`RequestOk` represents fetch fulfillment and the synchronous successful response check.
`Reject` represents a fetch error, non-OK response, or body error.
`BodyOk` represents a completed body read.
`AggregateReject` and `AggregateFulfill` represent the `Promise.all` reaction.
`Abort` represents the catch continuation and cancellation request.
`Throw` represents the continuation after `Promise.allSettled`, and `Return` represents the successful await.
The first rejection is recorded independently as expected history to check error preservation.

Two distinct assets each make one request and can each reject.
Pending requests and body reads may resolve or reject after abort because abort is cooperative.
The model abstracts away bytes and error details.
It assumes the first settled rejection determines the aggregate error and represents the synchronous response check atomically.
It does not assert eventual settlement, socket closure, or liveness.
`ReturnSettled`, `ThrowDrained`, and `ErrorPreserved` check success, drain, and error preservation.

## Child lifecycle

`Signal` represents a once-registered process signal callback, including a deferred spawn listener when the PID is unknown.
`Spawn` represents the spawn event and any deferred forwarding.
`SpawnError` represents the first child error event, including a failed spawn or a failed kill attempt.
`Close` resolves the close promise, and `Finish` represents its continuation and the `finally` cleanup.
The model conservatively permits signals after close and before that continuation.
This includes schedules that Node.js microtask ordering may exclude.
The recorded first signal is separate from the implementation's interruption state.

The model bounds one child and two distinct signals, each handled at most once.
The PID may already be known before the spawn event.
A failed spawn has no PID and emits an error before close.
The model conservatively permits a later child error while its listener is installed.
Kill records are attempts and do not guarantee termination.
A code and a signal close are distinct abstract results; their actual values are outside this model.
The model does not assert that the child eventually closes or that an unhandled signal has no effect on the parent process.
The listener set represents process signal listeners, while `deferred` represents the optional spawn listener.
The model does not track the removal of the child error and close listeners, which the runtime tests exercise.
`FirstSignal`, `Forwarding`, `NoEarlyResult`, `Cleanup`, and `ResultPriority` check signal order, forwarding, close ordering, cleanup, and outcome precedence.

## Maintenance and negative controls

Update the matching actions, assumptions, and properties when `downloadReleaseAssets` or `execute` changes.
Run both configurations after changes.
To check that the invariants detect regressions, copy the models and configurations to a temporary directory and separately apply each mutation there.
Remove the settlement guard from `Throw` to violate `ThrowDrained`.
Replace the assignment to `interrupted'` with `s` to violate `FirstSignal`.
Preserve `listeners` and `deferred` in `Finish` to violate `Cleanup`.
Replace the assignment to `firstError'` with `a` to violate `ErrorPreserved` when both assets reject.
Each mutated run should exit nonzero with the named invariant violation.
