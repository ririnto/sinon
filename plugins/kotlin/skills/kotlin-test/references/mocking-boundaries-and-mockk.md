---
title: Mocking Boundaries and MockK
description: >-
  Open this when mocking boundaries or MockK collaboration checks are the blocker.
---

Open this when deciding what stays real, what becomes a test double, and how to verify a Kotlin collaboration boundary with MockK.

## Rules

- mock collaboration boundaries, not simple values
- prefer real value objects and small in-memory fakes when they improve readability
- do not assert internal call choreography unless it is itself the public contract
- use `spyk` when you need real behavior plus selective verification
- use `coEvery` / `coVerify` for suspend-function mocks
- capture arguments with `slot` when the asserted value is computed inside the call

## Basic mock

```kotlin
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class InvoiceClientMockKTest {
    private val client: InvoiceClient = mockk()
    private val service = InvoiceService(client)

    @Test
    fun loadsTheRemoteInvoiceOnce() {
        every { client.load("inv-1") } returns Invoice("inv-1")

        assertEquals("inv-1", service.load("inv-1").id)
        verify(exactly = 1) { client.load("inv-1") }
    }
}
```

## Void functions and `just Runs`

```kotlin
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

class NotificationServiceTest {
    private val notifier: Notifier = mockk()
    private val service = OrderCompletionService(notifier)

    @Test
    fun sendsNotification() {
        every { notifier.send(any()) } just Runs

        service.completeOrder("order-1")

        verify { notifier.send(match { it.orderId == "order-1" }) }
    }
}
```

## Suspend function mocking

```kotlin
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Test
fun loadsRemoteProfile() = runTest {
    coEvery { api.fetchProfile("user-1") } returns Profile("user-1")

    val result = service.loadProfile("user-1")

    assertEquals("user-1", result.id)
    coVerify { api.fetchProfile("user-1") }
}
```

## Argument matching

```kotlin
import io.mockk.any
import io.mockk.capture
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArgumentMatchingTest {
    private val processor: PayloadProcessor = mockk()
    private val service = ProcessingService(processor)

    @Test
    fun capturesProcessedPayload() {
        val captured = slot<Payload>()
        every { processor.enqueue(capture(captured)) } returns JobId("job-1")

        service.process(rawInput)

        assertTrue(captured.isCaptured)
        assertEquals("processed", captured.captured.status)
    }
}
```

```kotlin
import io.mockk.any
import kotlin.test.Test
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify

class CacheInvalidationTest {
    private val cache: Cache = mockk()
    private val service = CacheService(cache)

    @Test
    fun matchesAnyArgument() {
        every { cache.invalidate(any<String>()) } just Runs

        service.clearCache()

        verify(atLeast = 1) { cache.invalidate(any()) }
    }
}
```

## Spy: wrap real objects

```kotlin
import io.mockk.spyk
import io.mockk.every
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals

class SpyRepositoryTest {
    @Test
    fun delegatesRealBehaviorAndVerifiesCall() {
        val realRepo = RealRepository()
        val spy: Repository = spyk(realRepo)

        every { spy.findByName("special") } returns SpecialItem

        val result = spy.findByName("normal")
        val special = spy.findByName("special")

        assertEquals(normalItem, result)
        assertEquals(SpecialItem, special)

        verify { spy.findByName("normal") }
        verify { spy.findByName("special") }
    }
}
```

## Object and static mocking

```kotlin
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.every
import io.mockk.verify
import kotlin.test.Test

class LoggerMockTest {
    private val service = WorkerService()

    @Test
    fun mocksSingletonObject() {
        mockkObject(Logger)

        every { Logger.info(any()) } just Runs

        service.doWork()

        verify { Logger.info(match { it.contains("work done") }) }

        unmockkObject(Logger)
    }
}
```

## Sequence returns and exceptions

```kotlin
import io.mockk.every
import io.mockk.returnsMany
import io.mockk.throws
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SequenceReturnsTest {
    private val counter: Counter = mockk()
    private val validator: InputValidator = mockk()

    @Test
    fun returnsDifferentValuesOnSubsequentCalls() {
        every { counter.next() } returnsMany listOf(1, 2, 3)

        assertEquals(1, counter.next())
        assertEquals(2, counter.next())
        assertEquals(3, counter.next())
    }

    @Test
    fun throwsOnInvalidInput() {
        every { validator.check("") } throws IllegalArgumentException("empty input")

        assertFailsWith<IllegalArgumentException> { validator.check("") }
    }
}
```

## Relaxed mocks

Use `relaxed = true` when the mock has many methods and you only care about a few interactions.

```kotlin
val complexService: ComplexService = mockk(relaxed = true)
every { complexService.compute(any()) } returns 42
```

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| mocking simple values or pure helpers | fixture becomes noisier than the behavior | keep simple pieces real |
| verifying every call just because a mock exists | test couples to implementation choreography | verify only the collaboration defining the contract |
| forgetting `coEvery`/`coVerify` for suspend functions | MockK does not intercept suspend calls with `every`/`verify` | always use coroutine-aware variants for suspend APIs |
| using `mockk` when `spyk` is needed | real behavior replaced by default returns | use `spyk(realObj)` to delegate to real implementation |
| over-relying on `relaxed = true` | silent default returns mask missing stubs | relax only when mock surface is large and most calls are irrelevant |
