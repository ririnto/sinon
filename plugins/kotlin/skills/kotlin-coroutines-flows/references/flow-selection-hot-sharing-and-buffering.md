---
title: Flow Selection, Hot Sharing, and Buffering
description: >-
  Open this when Flow justification, hot stream choices, sharing, replay, or buffering semantics are the actual blocker.
---

Open this when you need to justify `Flow`, choose between cold and hot streams, or explain sharing and buffering behavior.

## Decision Rules

- use `Flow` only when the contract emits values over time
- assume `Flow` is cold unless you intentionally share it
- use `StateFlow` for current state with an always-available latest value
- use `SharedFlow` for events or broadcasts with explicit replay
- add `buffer`, `conflate`, `stateIn`, `shareIn`, or `flowOn` only when their delivery tradeoffs are part of the design

## Patterns

Cold stream for ongoing updates:

```kotlin
fun observeOrders(): Flow<List<Order>> = repository.observeOrders()
```

Hot state from a cold upstream:

```kotlin
val orders: StateFlow<List<Order>> = orderService.stream()
    .stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )
```

Hot event stream with explicit replay:

```kotlin
val events: SharedFlow<ServerEvent> = server.events
    .shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        replay = 1
    )
```

Buffering with a clear tradeoff:

```kotlin
orders
    .buffer(16)
    .conflate()
    .collect { order ->
        process(order)
    }
```

Context change for upstream work only:

```kotlin
val parsed: Flow<Record> = rawLines
    .map(::parse)
    .flowOn(Dispatchers.Default)
```

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| returning `Flow` for one result | the stream has no honest streaming contract | use `suspend` |
| sharing every flow eagerly | lifecycle and replay semantics become hidden | keep the upstream cold until sharing is required |
| buffering without naming the drop or delay tradeoff | collectors see surprising delivery behavior | explain the buffering rule in the API design |
| using `flowOn` as a downstream dispatcher switch | only upstream work moves context | place it where the upstream boundary is intended |
