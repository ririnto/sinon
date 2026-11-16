# Coroutine and Flow decisions

## `suspend` vs `Flow`

Choose `suspend` when the operation produces one logical answer and then completes.

```kotlin
suspend fun loadOrder(orderId: OrderId): Order = repository.load(orderId)
```

Choose `Flow` when the contract is ongoing observation, repeated updates, or incremental delivery over time.

```kotlin
fun observeOrders(): Flow<List<Order>> = repository.observeOrders()
```

## `launch` vs `async`

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

## Cold `Flow` vs hot state or event streams

Use ordinary `Flow` as the default streaming type.
It is usually cold, so each collection starts the upstream work again unless you share it intentionally.

Use `StateFlow` when every collector should immediately see the latest state.
StateFlow always conflates -- fast writers drop intermediate values so collectors see at most the most recent emission.

```kotlin
private val mutableUiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.Loading)
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

private val mutableEvents: MutableSharedFlow<UiEvent> = MutableSharedFlow(
    replay = 0,
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
val events: SharedFlow<UiEvent> = mutableEvents
```

## Ownership and dispatchers

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

## Essential Flow operators

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
