---
metadata:
  reference:
    kotlinx.coroutines changelog:
      - version: 1.7.0
        url: https://github.com/Kotlin/kotlinx.coroutines/blob/1.7.0/CHANGES.md
      - version: 1.9.0
        url: https://github.com/Kotlin/kotlinx.coroutines/blob/1.9.0/CHANGES.md
    kotlinx.coroutines API:
      version: 1.11.0
      url:
        - https://github.com/Kotlin/kotlinx.coroutines/blob/1.11.0/kotlinx-coroutines-core/common/src/CoroutineDispatcher.kt
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
The referenced 1.11.0 source describes these examples.
Do not use its version as a standing install target.
Start with the smallest shape that matches the contract, then open a blocker reference only when scope, failure behavior, hot sharing, or concurrent mutation becomes the real problem.

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
- MUST keep code inside `flow { }` sequential and free of external context-switching calls.
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

## Key Decisions

### `suspend` vs `Flow`

Choose `suspend` when the operation produces one logical answer and then completes.

```kotlin
suspend fun loadOrder(orderId: OrderId): Order = repository.load(orderId)
```

Choose `Flow` when the contract is ongoing observation, repeated updates, or incremental delivery over time.

```kotlin
fun observeOrders(): Flow<List<Order>> = repository.observeOrders()
```

### `launch` vs `async`

Use `launch` for fire-and-forget work where the caller does not need the result.
The presenter pattern is the canonical example: the UI triggers an action and moves on.

```kotlin
class OrdersPresenter(private val presenterScope: CoroutineScope) {
    fun refresh() {
        presenterScope.launch {
            repository.refresh()
        }
    }
}
```

Use `async` only when the caller must await and compose results from multiple parallel operations.
Always call `await`.
`CoroutineExceptionHandler` does not handle `async` failures because `async` captures them in the returned `Deferred`.

```kotlin
suspend fun loadOrderWithItems(orderId: OrderId): Pair<Order, List<Item>> =
    coroutineScope {
        val orderDeferred = async { repository.loadOrder(orderId) }
        val itemsDeferred = async { repository.loadItems(orderId) }
        orderDeferred.await() to itemsDeferred.await()
    }
```

### Cold `Flow` vs hot state or event streams

Use ordinary `Flow` as the default streaming type.
It is usually cold, so each collection starts the upstream work again unless you share it intentionally.

Use `StateFlow` when every collector should immediately see the latest state.
StateFlow always conflates -- fast writers drop intermediate values so collectors see at most the most recent emission.

```kotlin
private val mutableUiState = MutableStateFlow(UiState.Loading)
val uiState: StateFlow<UiState> = mutableUiState

fun markLoaded(orders: List<Order>) {
    mutableUiState.update { UiState.Success(orders) }
}
```

Use `SharedFlow` when the stream represents events or broadcasts and replay must be chosen explicitly.
Configure buffer capacity and overflow policy to match the event volume.

```kotlin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

private val mutableEvents = MutableSharedFlow<UiEvent>(
    replay = 0,
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
val events: SharedFlow<UiEvent> = mutableEvents
```

### Ownership and dispatchers

Keep launched work attached to a visible owner.
The presenter pattern (shown above under "`launch` vs `async`") is the canonical form.
For service classes that own periodic or lifecycle-independent work, inject the scope:

```kotlin
class OrderSyncService(private val syncScope: CoroutineScope) {
    fun startPeriodicSync() {
        syncScope.launch {
            while (isActive) {
                sync()
                delay(60_000L)
            }
        }
    }
}
```

Keep blocking I/O boundaries explicit with `Dispatchers.IO`.

```kotlin
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CsvImporter {
    suspend fun import(path: Path): ImportResult = withContext(Dispatchers.IO) {
        parser.import(path)
    }
}
```

Keep CPU-heavy computation boundaries explicit with `Dispatchers.Default`.

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReportGenerator {
    suspend fun generate(rawData: RawData): Report = withContext(Dispatchers.Default) {
        heavyComputation(rawData)
    }
}
```

### Essential Flow operators

Build transform chains with basic operators.
Keep chains readable by grouping related transforms together.

```kotlin
repository.observeOrders()
    .filter { order -> order.status == Status.ACTIVE }
    .map(Order::toDisplayModel)
    .distinctUntilChanged()
    .collect(::render)
```

Use `flowOf(...)` for constant flows, `emptyFlow()` for completed flows, and `.asFlow()` to convert collections:

```kotlin
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

val single = flowOf(OrderId("1"))
val none: Flow<Order> = emptyFlow()
val fromList = listOf(1, 2, 3).asFlow()
```

Use `onEach` to inject side effects (logging, metrics) into a Flow chain without breaking the reactive style:

```kotlin
orders
    .onEach { order -> log.debug("Processing order ${order.id}") }
    .map(Order::toDisplayModel)
    .collect(::render)
```

Use `launchIn(scope)` as the idiomatic alternative to `scope.launch { flow.collect {} }` for collecting a flow into an external scope (common in UI code):

```kotlin
viewModel.orders
    .onEach(::render)
    .launchIn(viewModelScope)
```

Handle errors at the Flow level using `catch` and `retry`, not by wrapping `collect` in try/catch.
The `catch` operator intercepts upstream exceptions before they reach the collector.
`retry` re-subscribes the flow on failure.

```kotlin
repository.observeOrders()
    .retry(3) { error -> error is IOException }
    .catch { emit(FallbackOrderList) }
    .collect(::render)
```

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
| calling `withContext` inside `flow { }` | violates context-preservation invariant of Flow | move the context switch to `flowOn()` |
| assuming `StateFlow` emits every value | `StateFlow` conflates fast updates and drops intermediate values | use `SharedFlow` if every value matters |

## Scope Boundaries

Use this skill for coroutine structure, `suspend` versus `Flow`, cancellation-aware async design, hot or cold stream choices, and shared-state decisions directly caused by coroutine usage.

Do not use this skill as the primary source for general Kotlin language modeling, Kotlin test structure, framework-specific reactive APIs, Android architecture guidance, or JVM runtime internals.
