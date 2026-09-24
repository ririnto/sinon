---
description: >-
  Open this when virtual time, dispatcher injection, or deterministic coroutine scheduling is the blocker.
---

# Coroutine Test Determinism

Kotest test bodies are suspending.
Call suspend functions without a wrapper.
For controlled virtual time, enable `coroutineTestScope` on a test and use its `testCoroutineScheduler`.
Kotest uses `kotlinx-coroutines-test` internally, but direct scheduler calls in these examples need a test compile dependency on `kotlinx-coroutines-test`.
A plain suspending test without scheduler access does not need that direct dependency.

## Advance delayed work

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

class RetryClockTest : FunSpec({
    test("advances to the retry boundary").config(coroutineTestScope = true) {
        val deferred = async {
            delay(1_000)
            "done"
        }
        testCoroutineScheduler.advanceTimeBy(999)
        testCoroutineScheduler.runCurrent()
        deferred.isCompleted.shouldBeFalse()
        testCoroutineScheduler.advanceTimeBy(1)
        testCoroutineScheduler.runCurrent()
        deferred.await() shouldBe "done"
    }
})
```

Use `advanceUntilIdle()` when intermediate time boundaries do not matter.
Start delayed work before moving the scheduler, and call `runCurrent()` to execute tasks at the new time.

## Inject a dispatcher

When production code takes a `CoroutineDispatcher`, inject one tied to Kotest's scheduler.
This example also uses `StandardTestDispatcher` from the direct test dependency.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withContext

class OrderRepositoryTest : FunSpec({
    test("loads order on a test dispatcher").config(coroutineTestScope = true) {
        OrderRepository(client, StandardTestDispatcher(testCoroutineScheduler))
            .load(OrderId("o-1")).id shouldBe OrderId("o-1")
    }
}) {
    companion object {
        private class OrderRepository(
            private val client: OrderClient,
            private val dispatcher: CoroutineDispatcher = Dispatchers.IO
        ) {
            suspend fun load(orderId: OrderId): Order = withContext(dispatcher) {
                client.load(orderId)
            }
        }
    }
}
```

## Assert a suspending exception contract

The inline `shouldThrowExactly` block accepts a suspend call from the suspending test body.
Keep it outside the soft-assertion lambda and inspect its exact stable fields.

```kotlin
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PaymentServiceTest : FunSpec({
    test("rejects an expired token") {
        assertSoftly(shouldThrowExactly<TokenRejectedException> {
            service.authorize(ExpiredToken)
        }) {
            message shouldBe "token expired at 2026-01-01T00:00:00Z"
            tokenId shouldBe "token-1"
        }
    }
}) {
    companion object {
        private class TokenRejectedException(message: String, val tokenId: String) : RuntimeException(message)
    }
}
```

## Choose eager execution only when necessary

Kotest's default test dispatcher schedules launched tasks rather than executing them immediately.
Use `UnconfinedTestDispatcher` only when the test needs eager execution.
Eager execution can hide ordering bugs.
Prefer the standard test dispatcher and explicit scheduler advancement for timing-sensitive tests.
