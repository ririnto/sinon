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

The canonical cold `Flow`, `StateFlow`, and `SharedFlow` declaration patterns are in `SKILL.md` under "Cold `Flow` vs hot state or event streams". This reference covers only additive material.

Buffering with a clear tradeoff:

```kotlin
orders
    .buffer(16)
    .conflate()
    .collect { order ->
        process(order)
    }
```

Context change for upstream work only -- `flowOn` changes the dispatcher for upstream operations without affecting downstream collectors:

```kotlin
val parsed: Flow<Record> = rawLines
    .map(::parse)
    .flowOn(Dispatchers.Default)
```

Flow concurrency operators -- choose based on whether you need parallel, sequential, or switch-on-new semantics when transforming each emission into a sub-flow:

```kotlin
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flatMapLatest

val parallelResults: Flow<Result> = ids
    .flatMapMerge(concurrency = 4) { id -> fetchDetail(id) }

val sequentialResults: Flow<Result> = ids
    .flatMapConcat { id -> fetchDetail(id) }

val latestResults: Flow<SearchResult> = queries
    .flatMapLatest { query -> search(query) }
```

Use `flatMapMerge` for parallel I/O from a stream of inputs. Use `flatMapConcat` when order matters or resources must be shared sequentially. Use `flatMapLatest` when only the most recent result matters (the search debounce case).

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| returning `Flow` for one result | the stream has no honest streaming contract | use `suspend` |
| sharing every flow eagerly | lifecycle and replay semantics become hidden | keep the upstream cold until sharing is required |
| buffering without naming the drop or delay tradeoff | collectors see surprising delivery behavior | explain the buffering rule in the API design |
| using `flowOn` as a downstream dispatcher switch | only upstream work moves context | place it where the upstream boundary is intended |

## SharingStarted Decision Guide

| Strategy | Behavior | Use when |
| --- | --- | --- |
| `SharingStarted.Lazily` | Starts on first subscriber, stops when scope cancels | Long-lived state needed by late subscribers |
| `SharingStarted.Eagerly` | Starts immediately, stops when scope cancels | Upstream must run regardless of subscribers |
| `SharingStarted.WhileSubscribed(stopTimeoutMillis)` | Starts on first subscriber, stops after timeout since last subscriber left | UI state, resources that should release when unused |

Bridging a callback API into a Flow:

```kotlin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

fun observeLocationUpdates(): Flow<Location> = callbackFlow {
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            trySend(location)
        }
    }
    locationManager.requestLocationUpdates(listener)
    awaitClose { locationManager.removeUpdates(listener) }
}
```

Invariant: Inside `callbackFlow { }`, use `trySend()` (non-suspending) instead of `send()` (suspending) to avoid blocking the callback thread.

Flow error recovery chain:

```kotlin
rawEvents
    .map { parseEvent(it) }
    .catch { e ->
        logger.warn("Stream error, emitting fallback", e)
        emit(Event.Fallback)
    }
    .retry(3) { attempt, _ ->
        delay(attempt * 1_000L)
        true
    }
    .onStart { emit(Event.Connected) }
    .onCompletion { cause -> if (cause == null) emit(Event.Completed) }
    .collect { event -> handle(event) }
```

Stream composition patterns:

```kotlin
val combined: Flow<Combined> = zip(userFlow, ordersFlow) { user, orders ->
    Combined(user, orders)
}

val allUpdates: Flow<Update> = merge(flowA, flowB, flowC)

searchText
    .debounce(300)
    .mapLatest { query -> repository.search(query) }
    .collect { results -> showResults(results) }
```
