---
title: Kotlin Testing Patterns
description: >-
  Reference for Kotlin test naming, mocking boundaries, and exception-assertion shapes across JUnit and Kotest usage.
---

Use this reference when the real question is test shape, naming, or mocking boundaries.

Default test sequence:

1. Identify the observable behavior that matters.
2. Choose the smallest scope that proves that behavior.
3. Set up only the collaborators that influence the scenario.
4. Keep assertions centered on the contract, not incidental implementation details.

Naming guidance:

- prefer scenario-oriented names that make the behavior obvious
- keep one observable behavior per test when possible
- use `@Nested` with `inner` classes when a JUnit 5 scenario tree is clearer than flattening every context into a long test name
- use `@Timeout` for declarative limits and `assertTimeoutPreemptively` only when aborting the work is part of the test need

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class BillingPolicyTest {
    @Nested
    @DisplayName("when the invoice is overdue")
    inner class WhenInvoiceIsOverdue {
        @Test
        fun appliesLateFee() {
            assertEquals(Money("5.00"), service.lateFee())
        }
    }
}
```

```kotlin
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.DisplayName
import java.time.Duration

class RemotePricingTest {
    @DisplayName("aborts a slow refresh")
    @Test
    fun abortsSlowRefresh() {
        assertTimeoutPreemptively(Duration.ofMillis(200)) {
            service.refreshPrices()
        }
    }
}
```

`assertTimeoutPreemptively` runs work on a separate thread, so keep it away from `ThreadLocal`-sensitive framework scenarios.

Mocking guidance:

- mock collaboration boundaries, not simple values
- prefer real value objects and tiny in-memory fakes when they improve readability
- do not assert internal call choreography unless it is itself the public contract
- when a Kotlin codepath uses MockK, keep the example explicit about the shared boundary and verify only the collaboration that matters

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName

class InvoiceClientMockKTest {
    private val client: InvoiceClient = mockk()
    private val service = InvoiceService(client)

    @DisplayName("loads the remote invoice once")
    @Test
    fun loadsTheRemoteInvoiceOnce() {
        every { client.load("inv-1") } returns Invoice("inv-1")

        assertEquals("inv-1", service.load("inv-1").id)
        verify(exactly = 1) { client.load("inv-1") }
    }
}
```

Awaitility guidance:

- prefer Awaitility only when eventual consistency is real and bounded collection or scheduler control cannot prove the contract

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import org.awaitility.kotlin.await
import org.junit.jupiter.api.DisplayName
import java.time.Duration

class SettlementProjectionTest {
    @DisplayName("projects the settlement eventually")
    @Test
    fun projectsTheSettlementEventually() {
        service.startProjection()

        await.atMost(Duration.ofSeconds(5)).untilAsserted {
            assertEquals(ProjectionStatus.READY, repository.status())
        }
    }
}
```

Exception assertion guidance:

- when the exact exception type matters in Kotest, prefer `shouldThrowExactly<T>()` over broader exception assertions
- when the exception message is part of the contract, capture the returned exception and verify `message` with `assertEquals`

```kotlin
import io.kotest.assertions.throwables.shouldThrowExactly
import kotlin.test.assertEquals

class RetryPolicyKotestTest : FunSpec() {
    init {
        test("rejects invalid retry budget") {
            val error = shouldThrowExactly<RetryException> {
                service.run()
            }

            assertEquals("retry budget exhausted", error.message)
        }
    }
}
```
