---
name: kotlin-coroutines-flows
description: >-
  This skill should be used when the user asks to "use coroutines", "design a suspend API", "choose Flow vs suspend", "debug cancellation", "review Kotlin async code", or needs guidance on Kotlin coroutine and Flow patterns.
---

# Kotlin Coroutines and Flows

## Overview

Use this skill to design coroutine and Flow usage with structured concurrency, cancellation awareness, and clear async boundaries. The common case is deciding whether the API should return one logical result or a stream over time, then choosing the smallest coroutine structure that preserves ownership and cancellation semantics. Keep the async shape honest to the workload instead of adding reactive machinery by default.

## Use This Skill When

- You are designing or reviewing `suspend` APIs.
- You are choosing between `suspend` and `Flow`.
- You need to choose between `StateFlow`, `SharedFlow`, and ordinary cold `Flow`.
- You need to reason about scope ownership, cancellation, or dispatcher boundaries.
- Do not use this skill when the main problem is Kotlin language modeling or test structure rather than async design.

## Common-Case Workflow

1. Identify whether the caller needs one result, many results over time, or detached background work.
2. Prefer `suspend` for one-shot async work and `Flow` only for true stream semantics.
3. Keep scope ownership explicit and keep dispatcher switching at real blocking or CPU boundaries.
4. Escalate to deeper notes only if lifecycle, buffering, or operator choice is the real blocker.

## First Runnable Commands or Code Shape

Start from the smallest honest async API:

```kotlin
class OrderLoader {
    suspend fun loadOrder(orderId: OrderId): Order = repository.load(orderId)
}
```

*Applies when:* the caller wants one logical result rather than an ongoing stream.

## Ready-to-Adapt Templates

One-shot async work (use when the caller wants one logical result):

```kotlin
suspend fun loadOrder(orderId: OrderId): Order = repository.load(orderId)
```

Stream of updates (use when the caller reacts to changing state or repeated updates):

```kotlin
class OrderObserver {
    fun observeOrders(): Flow<List<Order>> = repository.observeOrders()
}
```

Hot state vs hot event stream (use when collectors need either the latest state on subscription or a non-replayed event stream):

```kotlin
class OrdersViewModel {
    private val _uiState = MutableStateFlow(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private val _events = MutableSharedFlow<UiEvent>(replay = 0)
    val events: SharedFlow<UiEvent> = _events
}
```

## Validate the Result

Validate the common case with these checks:

- `suspend` is used for one-shot results and `Flow` only for real stream semantics
- `StateFlow` is used for current state and `SharedFlow` only when replay and event semantics are explicitly chosen
- scope ownership is visible in the API or parent function
- blocking I/O is not hidden inside apparently lightweight coroutine paths
- cancellation is allowed to propagate instead of being swallowed

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| scope ownership, `coroutineScope`, `supervisorScope`, or dispatcher boundaries | `./references/coroutine-scope-patterns.md` |
| operator choice, buffering, or whether `Flow` is justified | `./references/flow-shaping.md` |

## Invariants

- MUST keep scope ownership explicit.
- SHOULD prefer `suspend` for one-shot async work.
- SHOULD prefer `Flow` only for true stream semantics.
- MUST preserve cancellation rather than swallowing it.
- MUST avoid inventing concurrency that the workload does not need.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| returning `Flow` for single-value work just to look reactive | the API gains machinery without better meaning | use `suspend` for one logical result |
| launching detached background work without a clear owner | cancellation and lifecycle become ambiguous | attach child work to an explicit parent scope |
| swallowing `CancellationException` inside broad exception handling | structured cancellation breaks silently | let cancellation propagate and clean up in `finally` if needed |
| mixing blocking I/O into coroutine paths without explicit boundaries | async code looks cheap but still blocks threads | keep dispatcher switching explicit at the real blocking boundary |

## Scope Boundaries

- Activate this skill for:
  - coroutine structure and scope ownership
  - Flow versus suspend decisions
  - cancellation-aware async design
- Do not use this skill as the primary source for:
  - general Kotlin language modeling
  - Kotlin test structure
  - `runTest`, virtual-time control, or Flow assertion strategy
  - Spring-specific reactive endpoints, `Mono`/`Flux` controller design, or `WebClient` usage
  - Java/JDK runtime tooling guidance
