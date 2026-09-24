---
description: >-
  Open this when mocking boundaries or MockK collaboration checks are the blocker.
---

# Mocking Boundaries and MockK

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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class InvoiceClientMockKTest : FunSpec({
    val client: InvoiceClient = mockk()
    val service = InvoiceService(client)
    test("loads the remote invoice once") {
        every { client.load("inv-1") } returns Invoice("inv-1")
        service.load("inv-1").id shouldBe "inv-1"
        verify(exactly = 1) { client.load("inv-1") }
    }
})
```

## Void functions and `just Runs`

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.mockk.Runs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class NotificationServiceTest : FunSpec({
    val notifier: Notifier = mockk()
    val service = OrderCompletionService(notifier)
    test("sends a notification") {
        every { notifier.send(any()) } just Runs
        service.completeOrder("order-1")
        verify { notifier.send(match { notification -> notification.orderId == "order-1" }) }
    }
})
```

## Suspend function mocking

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify

class ProfileServiceTest : FunSpec({
    test("loads a remote profile") {
        coEvery { api.fetchProfile("user-1") } returns Profile("user-1")
        service.loadProfile("user-1").id shouldBe "user-1"
        coVerify { api.fetchProfile("user-1") }
    }
})
```

## Argument matching

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

class ArgumentMatchingTest : FunSpec({
    val processor: PayloadProcessor = mockk()
    val service = ProcessingService(processor)
    test("captures the processed payload") {
        val captured: CapturingSlot<Payload> = slot()
        every { processor.enqueue(capture(captured)) } returns JobId("job-1")
        service.process(rawInput)
        captured.captured.status shouldBe "processed"
    }
})
```

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.mockk.Runs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class CacheInvalidationTest : FunSpec({
    val cache: Cache = mockk()
    val service = CacheService(cache)
    test("matches any argument") {
        every { cache.invalidate(any<String>()) } just Runs
        service.clearCache()
        verify(atLeast = 1) { cache.invalidate(any()) }
    }
})
```

## Spy: wrap real objects

```kotlin
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify

class SpyRepositoryTest : FunSpec({
    test("delegates real behavior and verifies the override") {
        val spy: Repository = spyk(RealRepository())
        every { spy.findByName("special") } returns SpecialItem
        assertSoftly(spy) {
            findByName("normal") shouldBe normalItem
            findByName("special") shouldBe SpecialItem
        }
        verify { spy.findByName("normal") }
        verify { spy.findByName("special") }
    }
})
```

## Object and static mocking

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.mockk.Runs
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify

class LoggerMockTest : FunSpec({
    val service = WorkerService()
    test("mocks a singleton object") {
        mockkObject(Logger)
        try {
            every { Logger.info(any()) } just Runs
            service.doWork()
            verify { Logger.info(match { message -> message.contains("work done") }) }
        } finally {
            unmockkObject(Logger)
        }
    }
})
```

## Sequence returns and exceptions

```kotlin
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class SequenceReturnsTest : FunSpec({
    val counter: Counter = mockk()
    val validator: InputValidator = mockk()
    test("returns values in sequence") {
        every { counter.next() } returnsMany listOf(1, 2, 3)
        listOf(counter.next(), counter.next(), counter.next()) shouldContainExactly listOf(1, 2, 3)
    }

    test("throws on invalid input") {
        every { validator.check("") } throws IllegalArgumentException("empty input")
        shouldThrowExactly<IllegalArgumentException> { validator.check("") }
            .message shouldBe "empty input"
    }
})
```

## Relaxed mocks

Use `relaxed = true` when the mock has many methods and you only care about a few interactions.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class ComplexServiceTest : FunSpec({
    test("stubs the relevant operation") {
        val complexService: ComplexService = mockk(relaxed = true)
        every { complexService.compute(any()) } returns 42
        complexService.compute(input) shouldBe 42
    }
})
```

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| mocking simple values or pure helpers | fixture becomes noisier than the behavior | keep simple pieces real |
| verifying calls unrelated to the contract | test couples to implementation choreography | verify the collaboration defining the contract |
| forgetting `coEvery`/`coVerify` for suspend functions | MockK does not intercept suspend calls with `every`/`verify` | always use coroutine-aware variants for suspend APIs |
| using `mockk` when `spyk` is needed | real behavior replaced by default returns | use `spyk(realObj)` to delegate to real implementation |
| over-relying on `relaxed = true` | silent default returns mask missing stubs | relax only when mock surface is large and most calls are irrelevant |
