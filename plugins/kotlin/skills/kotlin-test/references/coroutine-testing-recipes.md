---
title: Coroutine Testing Recipes
description: >-
  Reference for deterministic coroutine test patterns, dispatcher control, and virtual-time recipes in Kotlin tests.
---

Use this reference when suspend, Flow, cancellation, or virtual time behavior is under test.

Delay control example:

```kotlin
import kotlin.test.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName

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

Dispatcher injection example:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName

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

Avoid real sleeps, broad timeout assertions, and production dispatchers when deterministic scheduler control can prove the same behavior.
