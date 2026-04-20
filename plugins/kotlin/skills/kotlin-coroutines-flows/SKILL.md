---
name: kotlin-coroutines-flows
description: >-
  Use this skill when the user asks to "use coroutines", "design a suspend API", "choose Flow vs suspend", "debug cancellation", "review Kotlin async code", or needs guidance on Kotlin coroutine and Flow patterns.
---

## Goal

Design Kotlin coroutine and Flow code with honest async semantics, explicit ownership, and cancellation-safe behavior.

**Minimum Kotlin version: 1.9** -- examples use `kotlinx.coroutines` APIs stable since 1.6 (`limitedParallelism`, `callbackFlow`), `kotlinx.coroutines.test` APIs from 1.7+ (`runTest`, `TestDispatcher`), and Flow operators available since 1.5. The `kotlinx-coroutines` library version is managed through the project's dependency catalog; use 1.10.2 or later for full API coverage shown here. Start with the smallest shape that matches the contract, then open a blocker reference only when scope, failure behavior, hot sharing, or concurrent mutation becomes the real problem.

## Operating Rules

- MUST prefer `suspend` for one logical async result.
- MUST use `Flow` only when the contract delivers values over time.
- MUST keep coroutine ownership explicit through a caller, parent function, or injected `CoroutineScope`.
- MUST let `CancellationException` propagate.
- MUST keep blocking or CPU-heavy boundaries explicit with the right dispatcher or context hop.
- SHOULD treat ordinary `Flow` as cold and sequential unless sharing or buffering is chosen intentionally.
- SHOULD choose `StateFlow` for current state and `SharedFlow` for events or broadcasts.
- MUST choose `launch` for fire-and-forget work and `async` only when the caller awaits the result.
- MUST install `CoroutineExceptionHandler` only at a root scope or direct coroutine builder, never on child scopes.
- MUST keep code inside `flow { }` sequential and free of external context-switching calls.
- SHOULD use `MutableStateFlow.update { }` for atomic state transitions.
- MUST avoid `GlobalScope`, `GlobalScope.launch`, and detached work unless explicitly about background ownership.

## Common-Path Procedure

1. Decide whether the caller needs one result, a stream over time, or explicitly owned background work.
2. Start with `suspend` for one-shot work and switch to `Flow` only if the contract is truly streaming.
3. Make ownership visible by keeping child work inside the current suspend function or launching from an explicit parent scope.
4. Keep cancellation and failure semantics boring by default: child failure cancels the structured parent unless supervision is intentionally required.
5. Keep dispatcher changes at real blocking or CPU-heavy boundaries rather than scattering them across leaf code.
6. Escalate to a blocker reference only when hot sharing, buffering, supervision, or shared mutable state is the actual hard part.

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

Use `launch` for fire-and-forget work where the caller does not need the result. The presenter pattern is the canonical example: the UI triggers an action and moves on.

```kotlin
class OrdersPresenter(private val presenterScope: CoroutineScope) {
    fun refresh() {
        presenterScope.launch {
            repository.refresh()
        }
    }
}
```

Use `async` only when the caller must await and compose results from multiple parallel operations. Always call `await`; uncaught exceptions in orphaned `async` propagate as unhandled errors.

```kotlin
suspend fun loadOrderWithItems(orderId: OrderId): Pair<Order, List<Item>> =
    coroutineScope {
        val orderDeferred = async { repository.loadOrder(orderId) }
        val itemsDeferred = async { repository.loadItems(orderId) }
        orderDeferred.await() to itemsDeferred.await()
    }
```

### Cold `Flow` vs hot state or event streams

Use ordinary `Flow` as the default streaming type. It is usually cold, so each collection starts the upstream work again unless you share it intentionally.

Use `StateFlow` when every collector should immediately see the latest state. StateFlow always conflates -- fast writers drop intermediate values so collectors see at most the most recent emission.

```kotlin
private val _uiState = MutableStateFlow(UiState.Loading)
val uiState: StateFlow<UiState> = _uiState

fun markLoaded(orders: List<Order>) {
    _uiState.update { UiState.Success(orders) }
}
```

Use `SharedFlow` when the stream represents events or broadcasts and replay must be chosen explicitly. Configure buffer capacity and overflow policy to match the event volume.

```kotlin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

private val _events = MutableSharedFlow<UiEvent>(
    replay = 0,
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
val events: SharedFlow<UiEvent> = _events
```

### Ownership and dispatchers

Keep launched work attached to a visible owner. The presenter pattern (shown above under "`launch` vs `async`") is the canonical form. For service classes that own periodic or lifecycle-independent work, inject the scope:

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

Build transform chains with basic operators. Keep chains readable by grouping related transforms together.

```kotlin
repository.observeOrders()
    .filter { it.status == Status.ACTIVE }
    .map { it.toDisplayModel() }
    .distinctUntilChanged()
    .collect { model -> render(model) }
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
    .map { it.toDisplayModel() }
    .collect { model -> render(model) }
```

Use `launchIn(scope)` as the idiomatic alternative to `scope.launch { flow.collect {} }` for collecting a flow into an external scope (common in UI code):

```kotlin
viewModel.orders
    .onEach { orders -> render(orders) }
    .launchIn(viewModelScope)
```

Handle errors at the Flow level using `catch` and `retry`, not by wrapping `collect` in try/catch. The `catch` operator intercepts upstream exceptions before they reach the collector; `retry` re-subscribes the flow on failure.

```kotlin
repository.observeOrders()
    .retry(3) { it is IOException }
    .catch { e -> emit(FallbackOrderList) }
    .collect { orders -> render(orders) }
```

## First Safe Default

If you are unsure, start here:

```kotlin
class OrderLoader(private val repository: OrderRepository) {
    suspend fun load(orderId: OrderId): Order = repository.load(orderId)
}
```

Only add `Flow`, extra scopes, sharing, or buffering when the contract clearly needs them.

## Validate the Result

Check these pass/fail conditions before you stop:

- one-shot work uses `suspend` instead of a decorative stream
- stream APIs describe real ongoing delivery rather than single-response work
- `launch` is used for fire-and-forget and `async` only when the result is awaited
- `GlobalScope` is not used anywhere
- `CoroutineExceptionHandler` is installed only at root scope or direct coroutine builder
- code inside `flow { }` is sequential with no context-switching calls
- `StateFlow` and `SharedFlow` are chosen for clear state or event semantics; when using `SharedFlow`, imports include `BufferOverflow`
- scope ownership is visible and launched work is not detached by accident
- blocking or CPU-heavy work is not hidden inside an apparently cheap async path
- cancellation is preserved instead of swallowed in broad exception handling
- Flow error handling uses `catch`/`retry` operators instead of wrapping `collect` in try/catch
- Flow construction uses the right tool: `flowOf`/`asFlow`/`emptyFlow()` for constants, `flow {}` for custom logic, `callbackFlow` for callback bridging
- side effects in Flow chains use `onEach`; collection into external scopes uses `launchIn`

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| returning `Flow` for a single result | the API looks reactive without changing the contract | use `suspend` |
| launching work without a visible owner | lifecycle and cancellation become ambiguous | attach work to an explicit parent scope |
| swallowing `CancellationException` | structured cancellation silently breaks | let cancellation propagate and clean up in `finally` |
| sharing or buffering a flow by default | delivery semantics become harder to reason about | keep the flow cold and sequential until sharing is required |
| mutating shared state from multiple coroutines without a rule | race conditions become hidden design bugs | confine the state or protect it deliberately |
| using `GlobalScope` | escapes structured concurrency; work cannot be cancelled as a group | inject `CoroutineScope` |
| catching broad `Exception` in coroutine body | catches `CancellationException` and breaks cancellation | catch specific exceptions or rethrow `CancellationException` |
| using `async` without `await` | uncaught exceptions propagate as unhandled errors | use `launch` for fire-and-forget |
| installing `CoroutineExceptionHandler` on child scope | child handlers do not catch sibling failures | install only at root scope or direct builder |
| calling `withContext` inside `flow { }` | violates context-preservation invariant of Flow | move the context switch to `flowOn()` |
| assuming `StateFlow` emits every value | `StateFlow` conflates fast updates; intermediate values are dropped | use `SharedFlow` if every value matters |

## Output Contract

Return:

1. the chosen async shape and why it matches the contract
2. the ownership and cancellation model
3. any dispatcher, sharing, or buffering decisions that affect behavior
4. any blocker references needed for deeper branches

## Blocker References

Open these only when the named blocker is the real issue.

| Open this when... | Read... |
| --- | --- |
| you need `coroutineScope`, `supervisorScope`, explicit launch ownership, or dispatcher boundaries | `./references/scope-ownership-and-dispatchers.md` |
| you are debugging cancellation, timeouts, failure propagation, or cleanup semantics | `./references/cancellation-timeouts-and-failures.md` |
| you need to justify `Flow`, choose `StateFlow` or `SharedFlow`, or shape hot sharing and buffering | `./references/flow-selection-hot-sharing-and-buffering.md` |
| you are coordinating mutable state across coroutines and need a concurrency rule | `./references/shared-state-and-concurrency.md` |
| you need fan-in/fan-out, Channel handoff, work queues, or `select` expressions | `./references/shared-state-and-concurrency.md` |
| you are writing or debugging tests for coroutine or Flow code | `./references/testing.md` |

## Scope Boundaries

Use this skill for coroutine structure, `suspend` versus `Flow`, cancellation-aware async design, hot or cold stream choices, and shared-state decisions directly caused by coroutine usage.

Do not use this skill as the primary source for general Kotlin language modeling, Kotlin test structure, framework-specific reactive APIs, Android architecture guidance, or JVM runtime internals.
