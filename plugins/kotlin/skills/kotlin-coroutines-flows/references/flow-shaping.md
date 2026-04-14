---
title: Flow Shaping Guide
description: >-
  Reference for deciding when Flow is appropriate and how to shape StateFlow, SharedFlow, and shared upstream streams.
---

Use this reference when deciding whether `Flow` is justified and how the stream should be shaped.

Prefer `suspend` when:

- the caller wants one logical result
- the work is request-response in nature
- stream semantics would add machinery without clearer meaning

Prefer `Flow` when:

- values arrive over time
- the caller reacts to a changing source
- backpressure or incremental delivery is part of the contract

Use `StateFlow` when:

- every collector should see the latest state immediately on subscription
- one mutable state holder is easier to reason about than replaying ad hoc events

Use `SharedFlow` when:

- values are events or broadcasts rather than current state
- replay should be explicit and often stays at `0` for fire-once signals
- multiple collectors may observe the same event stream without a latest-value concept

## Concrete Flow Examples

StateFlow for UI state:

```kotlin
private val _uiState = MutableStateFlow(UiState.Loading)
val uiState: StateFlow<UiState> = _uiState

// In a ViewModel or presenter:
_uiState.value = UiState.Ready(data)

// In a collector:
scope.launch {
    uiState.collect { state ->
        render(state)  // always sees latest; no replay buffering needed
    }
}
```

SharedFlow for one-way events:

```kotlin
private val _events = MutableSharedFlow<UiEvent>(replay = 0)
val events: SharedFlow<UiEvent> = _events

// Emit — no one waits for the handler:
_events.emit(UiEvent.NavigateToDashboard)

// Collector:
scope.launch {
    events.collect { event ->
        handle(event)  // fire-and-forget from emitter's perspective
    }
}
```

Sharing a cold upstream with `stateIn` (one subscriber gets latest state immediately; additional subscribers get current state on arrival):

```kotlin
class OrdersViewModel {
    // Cold flow: each collector triggers a new collection from the upstream
    fun observeOrders(): Flow<List<Order>> = orderService.stream()

    // Share it as hot state — scope controls lifecycle, buffer drops if collector is slow:
    val orders: StateFlow<List<Order>> = orderService.stream()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
```

Sharing with `shareIn` for events or broadcasts:

```kotlin
val events: SharedFlow<ServerEvent> = server.events
    .shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        replay = 1  // replay last event to new subscribers
    )
```

Buffering a Flow to handle burst emissions without back-pressure on the upstream:

```kotlin
// Buffer emissions; conflate drops intermediate items if collector is slow:
orders.flow
    .buffer(16)           // extra buffer beyond the default 64
    .conflate()           // drop intermediate, keep latest
    .collect { order ->
        process(order)    // collector never blocks upstream
    }
```

`combine` for merging multiple live streams:

```kotlin
val combined: Flow<Dashboard> = combine(
    summaryFlow,
    alertsFlow,
    statusFlow
) { summary, alerts, status ->
    Dashboard(summary, alerts, status)
}
```

Operator guidance:

- use `map` and `filter` for direct transformations
- use `combine` only when multiple live streams must be merged as part of the contract
- use buffering and conflation only when you can explain the dropped or delayed behavior
- use `stateIn` or `shareIn` when a cold upstream must be shared intentionally rather than recollected per consumer

Testing note:

- keep test collection bounded to the behavior under assertion rather than collecting forever
- use `StateFlow` for steady state and `SharedFlow` for events so the test is asserting the right replay behavior in the first place
