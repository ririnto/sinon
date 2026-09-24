---
description: >-
  Open this when step-by-step Flow inspection, cancellation, or error-terminal assertions are the blocker.
---

# Turbine Flow Testing

Use Turbine when `first()` or `take(n).toList()` cannot prove emission order, silence, or a terminal event.
Add `app.cash.turbine:turbine` to `testImplementation` only when the project lacks it.
Use the project's managed version, or select a compatible stable release from Maven Central as described in `gradle-dependencies-and-config.md`.
Kotest test bodies are suspending, so Turbine's `test { }` needs no extra wrapper.

## Inspect ordered items

```kotlin
import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly

class UiStateRepositoryTest : FunSpec({
    test("emits loading, data, idle, then completes") {
        repository.observe().test {
            listOf(awaitItem(), awaitItem(), awaitItem()) shouldContainExactly listOf(
                UiState.Loading,
                UiState.Data(userId = "u-1"),
                UiState.Idle,
            )
            awaitComplete()
        }
    }
})
```

`awaitComplete()` verifies terminal completion.
`take(3).toList()` stops without proving completion.

## Assert silence and cancel

```kotlin
import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableSharedFlow

class TriggeredEventsTest : FunSpec({
    test("emits only after a trigger") {
        val events = MutableSharedFlow<String>()
        events.test {
            expectNoEvents()
            events.emit("ready")
            awaitItem() shouldBe "ready"
            cancelAndIgnoreRemainingEvents()
        }
    }
})
```

`expectNoEvents()` checks only events already available, then `awaitItem()` verifies the event after the trigger.
Use a bounded virtual-time test when silence for a specified interval is the contract.

## Verify cleanup

```kotlin
import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow

class CancellationTest : FunSpec({
    test("cancels collection and runs cleanup") {
        var cleanedUp = false
        flow {
            try {
                emit(UiState.Loading)
                awaitCancellation()
            } finally {
                cleanedUp = true
            }
        }.test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        cleanedUp.shouldBeTrue()
    }
})
```

## Inspect a terminal error

```kotlin
import app.cash.turbine.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.IOException
import kotlinx.coroutines.flow.flow

class FailureFlowTest : FunSpec({
    test("propagates the network failure") {
        flow<Data> { throw IOException("connection refused") }.test {
            awaitError().shouldBeInstanceOf<IOException>().message shouldBe "connection refused"
        }
    }
})
```

## Inspect concurrent flows

`turbineScope` verifies that received events were consumed when it exits.
Cancel each Turbine when using an ordinary `coroutineScope` because that scope does not cancel them on exit.

```kotlin
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.coroutineScope

class DashboardRepositoryTest : FunSpec({
    test("observes state and events together") {
        coroutineScope {
            turbineScope {
                val states = repository.observeState().testIn(this@coroutineScope)
                val events = repository.observeEvents().testIn(this@coroutineScope)
                try {
                    repository.refresh()
                    states.awaitItem() shouldBe UiState.Loading
                    events.awaitItem() shouldBe UiEvent.RefreshStarted
                } finally {
                    states.cancelAndIgnoreRemainingEvents()
                    events.cancelAndIgnoreRemainingEvents()
                }
            }
        }
    }
})
```

Finish every Turbine with `awaitComplete()`, `awaitError()`, or a cancellation operation.
