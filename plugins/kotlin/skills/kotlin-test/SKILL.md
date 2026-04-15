---
name: kotlin-test
description: >-
  Use this skill when the user asks to "test Kotlin code", "write a coroutine test", "mock a Kotlin dependency", "structure Kotlin tests", or needs guidance on practical Kotlin testing patterns.
---

# Kotlin Test

## Overview

Use this skill to write clear, deterministic Kotlin tests with an emphasis on behavioral scope, coroutine-aware execution, and readable fixtures. The common case is not building a perfect test pyramid; it is choosing the smallest test that proves one observable behavior. Keep the test intent obvious, then add coroutine tooling only when async semantics actually matter.

Example conventions:

- test classes use PascalCase
- test functions use camelCase
- human-facing explanation belongs in `@DisplayName`
- test annotations and baseline assertions come from `kotlin.test.*`
- code examples should stay ktlint-friendly by default
- avoid unnecessary blank lines inside function bodies
- use infix assertions where the chosen test library makes them read more clearly
- each file-oriented example keeps exactly one top-level root type
- the root declaration is a type, not a top-level `fun` or `val`

## Use This Skill When

- You are adding or fixing Kotlin unit or integration tests.
- You need to test `suspend` functions or `Flow` behavior.
- You need to decide what should be mocked and what should stay real.
- Do not use this skill when the main problem is Spring test-slice selection or full application-context wiring rather than Kotlin test structure itself.

## Common-Case Workflow

1. Read the production behavior and the nearest related tests first.
2. Choose the smallest test scope that proves the requested behavior.
3. Start with a plain assertion shape, then add coroutine-aware tooling if the code under test uses `suspend`, `Flow`, delay, or cancellation semantics.
4. Escalate to deeper recipes only if virtual time, Flow sampling, or mocking boundaries are the real blocker.

## Minimal Setup

Add coroutine-aware test tooling only in test scope:

```kotlin
dependencies {
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("org.awaitility:awaitility-kotlin")
    testImplementation("io.mockk:mockk")
}
```

Add Kotest when the repository wants matcher-heavy or spec-style tests:

```kotlin
dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform("io.kotest:kotest-bom"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.kotest:kotest-runner-junit5")
    testImplementation("io.kotest:kotest-assertions-core")
    testImplementation("org.awaitility:awaitility-kotlin")
    testImplementation("io.mockk:mockk")
}
```

Setup rules:

- keep `kotlinx-coroutines-test` aligned with the kotlinx-coroutines version already used in the project when the build tool or version catalog resolves it separately
- use bounded Flow collection such as `first()`, `take(n)`, or `toList()` before adding extra test helpers
- add `awaitility-kotlin` only for genuinely eventual behavior that cannot be proven with deterministic coroutine scheduler control alone
- add MockK when Kotlin-specific mocking is needed and the repository prefers it over Mockito-style doubles
- use the JUnit 5 bridge when combining `kotlin.test.Test` with `@DisplayName`
- when several JUnit assertions describe one observable behavior, prefer `assertAll`; when using Kotest, prefer `assertSoftly`
- when a Kotest example proves exception type precisely, prefer `shouldThrowExactly<T>()` and verify the returned message with `assertEquals` when it matters

## First Runnable Commands or Code Shape

Start with one behavior-focused test:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.DisplayName

class ProfileServiceTest {
    @DisplayName("returns cached profile when cache hit exists")
    @Test
    fun returnsCachedProfileWhenCacheHitExists() {
        val result = service.loadProfile("user-1")
        assertEquals(Profile("user-1"), result)
    }
}
```
Use when: the code path is synchronous and the test question is simply "what behavior should be proven?"

## Ready-to-Adapt Templates

Pure behavior-focused unit test:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.DisplayName

class ProfileServiceTest {
    @DisplayName("returns cached profile when cache hit exists")
    @Test
    fun returnsCachedProfileWhenCacheHitExists() {
        val result = service.loadProfile("user-1")
        assertEquals(Profile("user-1"), result)
    }
}
```
Use when: the behavior is synchronous and local.

Kotest matcher shape:

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe

class ProfileServiceKotestTest : FunSpec() {
    init {
        test("returns cached profile when cache hit exists") {
            val result = service.loadProfile("user-1")
            assertSoftly(result) {
                it shouldBe Profile("user-1")
                it.id shouldBe "user-1"
            }
        }
    }
}
```
Use when: the repository already prefers Kotest matchers, infix assertions, and grouped soft assertions.

Suspend function test:

```kotlin
import kotlin.test.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName

class OrderSummaryServiceTest {
    @DisplayName("loads order summary")
    @Test
    fun loadsOrderSummary() = runTest {
        val result = service.loadSummary(OrderId("o-1"))
        assertEquals(OrderSummary("o-1"), result)
    }
}
```
Use when: the code under test uses `suspend` semantics.

Flow assertion shape:

```kotlin
import kotlin.test.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName

class UiStateRepositoryTest {
    @DisplayName("emits loading then data")
    @Test
    fun emitsLoadingThenData() = runTest {
        val items = repository.observe().take(2).toList()
        assertAll(
            { assertEquals(UiState.Loading, items[0]) },
            { assertEquals(UiState.Data, items[1]) },
        )
    }
}
```
Use when: you only need a small, deterministic sample from a Flow.

Kotest infix assertion shape:

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList

class OrderSummaryKotestTest : FunSpec() {
    init {
        test("loads order summary") {
            val result = service.loadSummary(OrderId("o-1"))
            result shouldBe OrderSummary("o-1")
        }
    }
}
```
Use when: infix assertions make the expected contract easier to scan.

Nested JUnit 5 context shape:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class CartServiceTest {
    @Nested
    @DisplayName("when the cart is empty")
    inner class WhenCartIsEmpty {
        @Test
        fun returnsZeroTotal() {
            assertEquals(0, service.total())
        }
    }
}
```
Use when: one behavior splits into a few context groups and an `inner` nested class makes the scenario tree easier to scan.

JUnit 5 timeout shape:

```kotlin
import kotlin.test.Test
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class FeedRefreshTest {
    @DisplayName("refresh finishes within budget")
    @Test
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
    fun refreshFinishesWithinBudget() {
        service.refresh()
    }
}
```
Use when: the contract includes an ordinary time budget and the test does not need preemptive interruption.

MockK boundary shape:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.DisplayName
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ClientServiceMockKTest {
    private val client: RemoteClient = mockk()
    private val service = ClientService(client)

    @DisplayName("retries after transient failure")
    @Test
    fun retriesAfterTransientFailure() {
        every { client.call() } throws IllegalStateException("temporary") andThen "ok"

        assertEquals("ok", service.run())
        verify(exactly = 2) { client.call() }
    }
}
```
Use when: the repository already standardizes MockK and the mock boundary is a real collaborator.

Awaitility Kotlin shape:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import org.awaitility.kotlin.await
import org.junit.jupiter.api.DisplayName
import java.time.Duration

class EventPublisherAwaitilityTest {
    @DisplayName("publishes eventually")
    @Test
    fun publishesEventually() {
        service.triggerAsyncWork()

        await.atMost(Duration.ofSeconds(5)).untilAsserted {
            assertEquals(Status.DONE, repository.status())
        }
    }
}
```
Use when: the code really is eventually consistent and deterministic coroutine scheduler control cannot prove the same behavior.

## Validate the Result

Validate the common case with these checks:

- each test proves one observable behavior
- coroutine tests use deterministic scheduler control instead of real sleeps
- coroutine test dependencies are present in test scope instead of being assumed implicitly
- mocks exist only at real collaboration boundaries
- Flow tests collect only the amount needed for the assertion

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| delay control, scheduler advancement, or dispatcher injection | `./references/coroutine-test-determinism.md` |
| Flow replay or bounded collection shape | `./references/flow-testing.md` |
| test shape, naming, or mocking boundaries | `./references/test-structure-and-mocking.md` |

## Invariants

- MUST choose the smallest test scope that proves the behavior.
- SHOULD keep one observable behavior per test.
- MUST use coroutine-aware testing where async semantics matter.
- SHOULD inject dispatchers instead of hardcoding them when coroutine code needs deterministic tests.
- MUST avoid flaky timing-based assertions when deterministic alternatives exist.
- SHOULD keep test names scenario-based and intention-revealing.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| asserting internal call order instead of behavior | the test becomes coupled to implementation noise | assert the visible contract first |
| using real delays in coroutine tests | the suite becomes slow and flaky | use `runTest` and virtual time control |
| hardcoding production dispatchers in coroutine code under test | scheduler control is lost in tests | inject a dispatcher or dispatcher provider so tests can pass a test dispatcher |
| over-mocking simple values or pure helpers | fixtures become harder to read than the code under test | keep simple values real |
| collecting a Flow forever | the test never reaches a bounded assertion | use `take`, `first`, or a short bounded collection |

## Scope Boundaries

- Activate this skill for:
  - Kotlin unit and integration test shape
  - coroutine-aware Kotlin testing
  - mock and fixture decisions in Kotlin code
- Do not use this skill as the primary source for:
  - coroutine API design or `suspend` versus `Flow` contract decisions
  - general Kotlin language refactors
  - Spring-context testing
  - Java/JDK tool concerns
