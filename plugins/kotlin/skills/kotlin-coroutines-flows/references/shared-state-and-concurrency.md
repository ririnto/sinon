---
title: Shared State and Concurrency
description: >-
  Open this when multiple coroutines touch the same mutable state and you need an explicit concurrency rule.
---

Open this when concurrent mutation is the design problem rather than API shape alone.

## Rules

- prefer immutable messages between coroutines when possible
- confine mutable state to one coroutine or one dispatcher before adding locks
- use atomics for simple counters or flags
- use `Mutex` when multiple suspending operations must update shared state safely
- do not assume `volatile` makes compound updates safe

## Patterns

Thread confinement through one owner:

```kotlin
class OrderAccumulator {
    private val updates = MutableSharedFlow<Order>()
    private val _state = MutableStateFlow(emptyList<Order>())
    val state: StateFlow<List<Order>> = _state

    suspend fun run() {
        updates.collect { order ->
            _state.value = _state.value + order
        }
    }

    suspend fun submit(order: Order) {
        updates.emit(order)
    }
}
```

Simple atomic update:

```kotlin
import java.util.concurrent.atomic.AtomicInteger

private val nextId = AtomicInteger(0)

fun allocateId(): Int = nextId.incrementAndGet()
```

Protected suspending update with `Mutex`:

```kotlin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Inventory {
    private val mutex = Mutex()
    private val stock = mutableMapOf<Sku, Int>()

    suspend fun reserve(sku: Sku, amount: Int) {
        mutex.withLock {
            stock[sku] = (stock[sku] ?: 0) - amount
        }
    }
}
```

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| reading and writing shared mutable data from many coroutines | races stay intermittent and hard to prove | confine the state or guard it explicitly |
| using `volatile` for multi-step updates | visibility is not atomicity | use atomics or `Mutex` |
| locking around too much unrelated work | contention hides the real state boundary | keep the protected section small and explicit |
