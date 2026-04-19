---
title: Coroutine Test Determinism
description: >-
  Open this when virtual time, dispatcher injection, or deterministic coroutine scheduling is the blocker.
---

Use this reference when the job is to make one coroutine test deterministic. This reference should be sufficient on its own for that task.

Use this file to finish one of these jobs:

- control virtual time without real sleeping
- inject a test dispatcher instead of using production dispatchers
- assert suspend behavior in Kotest or JUnit without flaky timing
- prove delayed retries or scheduled work using the test scheduler

Delay control example:

```kotlin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RefreshServiceTest {
    @DisplayName("refresh retries after delay")
    @Test
    fun refreshRetriesAfterDelay() = runTest {
        val job = launch { service.refresh() }
        advanceUntilIdle()
        job.cancel()
    }
}
```

Use when: the production code delays or retries and the test should advance the scheduler instead of waiting in real time.

Dispatcher injection example:

```kotlin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class OrderRepositoryTest {
    private class OrderRepository(
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        suspend fun load(orderId: OrderId): Order = withContext(dispatcher) {
            client.load(orderId)
        }
    }

    @DisplayName("loads order on a test dispatcher")
    @Test
    fun loadsOrderOnATestDispatcher() = runTest {
        val repository = OrderRepository(StandardTestDispatcher(testScheduler))
        val result = repository.load(OrderId("o-1"))
        assertEquals(OrderId("o-1"), result.id)
    }
}
```

Use when: the code under test normally uses `Dispatchers.IO` or another production dispatcher and the test must replace it with deterministic scheduler control.

Stepwise virtual-time control:

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RetryClockTest {
    @Test
    fun advancesTimeStepByStep() = runTest {
        val deferred = async {
            delay(1_000)
            "done"
        }

        advanceTimeBy(999)
        assertEquals(false, deferred.isCompleted)

        advanceTimeBy(1)
        assertEquals("done", deferred.await())
    }
}
```

Use when: the exact delay boundary matters and `advanceUntilIdle()` would skip too much intermediate timing detail.

Kotest coroutine matcher shape:

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class OrderRepositoryKotestTest : FunSpec() {
    private class OrderRepository(
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        suspend fun load(orderId: OrderId): Order = withContext(dispatcher) {
            client.load(orderId)
        }
    }

    init {
        test("loads order on a test dispatcher") {
            runTest {
                val repository = OrderRepository(StandardTestDispatcher(testScheduler))
                assertSoftly(repository.load(OrderId("o-1"))) {
                    it.id shouldBe OrderId("o-1")
                }
            }
        }
    }
}
```

Use when: the project already uses Kotest and the coroutine assertion should stay inside the existing matcher style.

Failure assertion for suspend work:

```kotlin
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentServiceTest {
    @Test
    fun rejectsExpiredToken() {
        assertThrows<IllegalStateException> {
            runTest {
                service.authorize(ExpiredToken)
            }
        }
    }
}
```

Use when: the coroutine path should fail fast and the test needs to prove the thrown exception directly.

Avoid real sleeps, broad timeout assertions, and production dispatchers when deterministic scheduler control can prove the same behavior. The JUnit examples in this file use JUnit consistently; the Kotest example is the deliberate exception for Kotest-based suites.
