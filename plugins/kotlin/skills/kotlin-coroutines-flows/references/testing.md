---
description: >-
  Open this when testing suspend functions, Flow emissions, cancellation, or virtual time.
---

# Testing Coroutines and Flows

This reference is a bridge from coroutine design to the `kotest` skill's test guidance.
Kotest test bodies are suspending.
Ordinary suspend calls and bounded Flow collection need no coroutine-test wrapper.
Enable Kotest `coroutineTestScope` only when test-dispatcher control or virtual time is essential.

## Test a suspend result

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OrderLoaderTest : FunSpec({
    test("returns the loaded order") {
        OrderLoader(FakeOrderRepository()).load(OrderId("123")).id.value shouldBe "123"
    }
})
```

## Control virtual time

Kotest's `coroutineTestScope` provides a test dispatcher and scheduler through the framework.
`kotlinx-coroutines-test` implements that scheduler.
Add it to `testImplementation` when calling scheduler methods because Kotest does not expose its internal dependency on the consumer's compile classpath.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async

class TimeoutTest : FunSpec({
    test("returns fallback after timeout").config(coroutineTestScope = true) {
        val result = async { slowRepository.loadWithTimeout(OrderId("1")) }
        testCoroutineScheduler.advanceTimeBy(5_000)
        testCoroutineScheduler.runCurrent()
        result.await() shouldBe Fallback
    }
})
```

## Bound Flow collection

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class StateFlowTest : FunSpec({
    test("replays the latest state") {
        val state = MutableStateFlow<UiState>(UiState.Loading)
        state.first() shouldBe UiState.Loading
        state.value = UiState.Data(order)
        state.first() shouldBe UiState.Data(order)
    }
})
```

If an intermediate `StateFlow` value must be observed, start collection before changing the state.
Use `take(n).toList()` or Turbine for a bounded sequence.

## Verify cancellation cleanup

Use `coroutineScope` for a child job when no virtual scheduler is needed.
The child must start before cancellation so its `finally` block can run.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class CleanupTest : FunSpec({
    test("closes a cancelled resource") {
        coroutineScope {
            val resource = TrackingResource()
            val job = launch(start = CoroutineStart.UNDISPATCHED) { resource.longOperation() }
            job.cancel()
            job.join()
            resource.wasClosed.shouldBeTrue()
        }
    }
})
```

Avoid `runBlocking` when the Kotest body already suspends.
Do not collect an unbounded Flow in a test body.
