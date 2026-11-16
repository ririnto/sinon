---
metadata:
  reference:
    kotlinx.coroutines changelog:
      - version: 1.7.0
        url: https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.7.0/CHANGES.md
      - version: 1.9.0
        url: https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.9.0/CHANGES.md
    kotlinx.coroutines API:
      version: 1.11.0
      url:
        - https://raw.githubusercontent.com/Kotlin/kotlinx.coroutines/1.11.0/kotlinx-coroutines-core/common/src/CoroutineDispatcher.kt
        - https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/coroutine-scope.html
        - https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/supervisor-scope.html
        - https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/cancel-children.html
        - https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-job/join.html
    Kotest test coroutine dispatcher:
      version: 6.2
      url: https://kotest.io/docs/framework/coroutines/test-coroutine-dispatcher.html
name: kotlin-coroutines-flows
description: >-
  Design or review Kotlin suspend and Flow APIs, coroutine ownership, cancellation, or shared-state behavior.
---

# Kotlin Coroutines Flows

## Goal

Design Kotlin coroutine and Flow code with honest async semantics, explicit ownership, and cancellation-safe behavior.

Minimum Kotlin version: 2.1 -- examples use `kotlinx.coroutines` APIs (Flow stable since 1.3, `callbackFlow` since 1.5, and `limitedParallelism` stable since 1.9).
Use the project's managed `kotlinx-coroutines` version when it supports the APIs shown here.
For a new dependency or required upgrade, check Maven Central for the latest stable release compatible with the project's Kotlin version before updating its catalog.
Do not use example versions as standing install targets.
Start with the smallest shape that matches the contract, then read the references for the scope, failure behavior, hot sharing, or concurrent mutation involved.

## Operating Rules

- MUST prefer `suspend` for one logical async result.
- MUST use `Flow` only when the contract delivers values over time.
- MUST keep coroutine ownership explicit through a caller, parent function, or injected `CoroutineScope`.
- MUST let `CancellationException` propagate.
- MUST keep blocking or CPU-heavy boundaries explicit with the right dispatcher or context hop.
- SHOULD treat ordinary `Flow` as cold and sequential unless sharing or buffering is chosen intentionally.
- SHOULD choose `StateFlow` for current state and `SharedFlow` for events or broadcasts.
- MUST choose `launch` for fire-and-forget work and `async` only when the caller awaits the result.
- MUST install `CoroutineExceptionHandler` only on root coroutine contexts or root `launch` builders where uncaught exceptions are reported.
  - Do not rely on it for child coroutines or `async` results.
- Keep emissions inside `flow { }` in the collector's context.
  A context switch may compute a value if emission occurs after returning to the original context.
- SHOULD use `MutableStateFlow.update { }` for atomic state transitions.
- MUST avoid `GlobalScope`, `GlobalScope.launch`, and detached work unless explicitly about background ownership.

## Task Context

Read the async contract and its callers to establish result shape, lifecycle owner, and cancellation behavior.
Choose `suspend`, streaming, or explicitly owned background work from that contract.
Use the reference table below when ownership, failure propagation, hot sharing, or concurrent mutation needs detail.

## References

Read the references that match the current decision.

| Open this when... | Read... |
| --- | --- |
| you need `coroutineScope`, `supervisorScope`, explicit launch ownership, or dispatcher boundaries | `./references/scope-ownership-and-dispatchers.md` |
| you are debugging cancellation, timeouts, failure propagation, or cleanup semantics | `./references/cancellation-timeouts-and-failures.md` |
| you need to justify `Flow`, choose `StateFlow` or `SharedFlow`, or shape hot sharing and buffering | `./references/flow-selection-hot-sharing-and-buffering.md` |
| you are coordinating mutable state across coroutines, or need fan-in/fan-out, Channel handoff, work queues, or `select` expressions | `./references/shared-state-and-concurrency.md` |
| you are checking coroutine behavior from this skill and need a minimal testing bridge | `./references/testing.md` |

## Applying These Rules

Before choosing or changing `suspend`, `Flow`, `launch`, `async`, stream sharing, dispatchers, or Flow operators, read [coroutine and Flow decisions](./references/key-decisions.md).
`StateFlow` conflates fast updates; choose replay, buffering, and overflow explicitly for `SharedFlow` events.
Handle Flow failures with `catch` and `retry` at the appropriate upstream boundary, while preserving cancellation.

## Completion

Explain the async shape, ownership, cancellation, and any dispatcher or delivery tradeoff relevant to the task.
For implementation work, verify changed behavior with affected native tests rather than testing every coroutine pattern.
Report any unverified lifecycle or concurrency boundary.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| returning `Flow` for a single result | the API looks reactive without changing the contract | use `suspend` |
| launching work without a visible owner | lifecycle and cancellation become ambiguous | attach work to an explicit parent scope |
| swallowing `CancellationException` | structured cancellation silently breaks | let cancellation propagate and clean up in `finally` |
| sharing or buffering a flow by default | delivery semantics become harder to reason about | keep the flow cold and sequential until sharing is required |
| mutating shared state from multiple coroutines without a rule | race conditions become hidden design bugs | confine the state or protect it deliberately |
| using `GlobalScope` | escapes structured concurrency, so work cannot be cancelled as a group | inject `CoroutineScope` |
| catching broad `Exception` in coroutine body | catches `CancellationException` and breaks cancellation | catch specific exceptions or rethrow `CancellationException` |
| using `async` without `await` | uncaught exceptions propagate as unhandled errors | use `launch` for fire-and-forget |
| installing `CoroutineExceptionHandler` on child scope or expecting it to handle `async` failures | child handlers do not catch sibling failures, and `async` captures failures in `Deferred` | install handlers only at root contexts and handle `async` with `await` |
| emitting from inside a changed context in `flow { }` | violates the context-preservation invariant of Flow | compute in a context hop, then emit in the original context, or move the switch to `flowOn()` |
| assuming `StateFlow` emits every value | `StateFlow` conflates fast updates and drops intermediate values | use `SharedFlow` if every value matters |

## Scope Boundaries

Do not use this skill as the primary source for general Kotlin language modeling, Kotlin test structure, framework-specific reactive APIs, Android architecture guidance, or JVM runtime internals.
