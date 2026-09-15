---
name: kotlin-test
description: >-
  Write or fix Kotlin JVM tests, including deterministic coroutine, Flow, and exception-contract tests.
---

# Kotlin Test

## Goal

Write clear, deterministic Kotlin tests by proving one observable behavior with the smallest scope that works.

Example baseline: Kotlin 2.1 -- examples use `kotlin.test` baseline assertions, `kotlinx.coroutines.test` (1.7+), JUnit 5 Jupiter APIs, MockK 1.14+, Kotest 6.x, and Turbine 1.2+.
All library versions are managed through the project's dependency catalog.
Use existing library versions unless a required feature justifies an authorized dependency change.
This skill covers JVM testing only -- for multiplatform targets, adapt assertions to `kotlin-test-js` or `kotlin-test-native`.
Keep the common path centered on `kotlin.test`, `runTest` for suspend code, bounded Flow collection, and direct exception assertions.
Use blocker references only when virtual time, replay semantics, mocking-library details, or JUnit 5 structure features become the real problem.

## Operating Rules

- MUST select tests from acceptance criteria and regression risks, reusing existing coverage when sufficient.
- SHOULD prefer a unit test when it can prove the behavior.
- MUST use an integration test only when the behavior requires a real process, database, network, filesystem boundary, container, or framework runtime.
- MUST reserve end-to-end tests for distinct core user journeys that lower-level tests do not already prove.
- MUST NOT impose test-layer ratios or add every layer for each change.
- MUST use the repository's native test runner without adding a task-specific execution wrapper.
- MUST NOT test prose instructions, headings, wording, word counts, or declared file lists when review is sufficient.
- SHOULD keep one observable behavior per test.
- SHOULD name tests as `verbCondition` or `subjectVerb` describing the observable behavior (e.g., `returnsCachedProfile`, `emitsLoadingThenData`, `rejectsInvalidInput`).
- SHOULD use `kotlin.test` annotations and baseline assertions as the default surface.
- MUST use `runTest` when coroutine semantics actually matter.
- SHOULD keep Flow assertions bounded with `first()`, `single()`, or `take(n).toList()`.
- SHOULD use `assertFailsWith<T>()` when exception type is part of the contract.
- MUST assert the caught exception's message and meaningful fields when the exception contract matters, not only the type.
- MUST NOT introduce Kotest into a `kotlin.test` or JUnit suite just for its exception helpers, and MUST NOT add Kotest assertions inside ktlint-rule or other library-native test harnesses.
- MUST compare serialized output with full equality after parsing structured formats into exact fields or elements.
  - Use containment only when membership itself is the observable contract.
- MUST avoid real sleeps when deterministic scheduler control can prove the same behavior.
- SHOULD keep mocks at collaboration boundaries and keep simple values real.

## Task Context

Read the production contract and related tests before choosing test scope.
Reuse the suite's existing runner, assertions, and fixtures.
Use `runTest` only when coroutine semantics matter and `kotlinx-coroutines-test` is available.
Use the reference table below for the test behavior or execution problem under change.

## References

Read the references that match the current decision.

| Open when... | Read... |
| --- | --- |
| step-by-step Flow inspection, cancellation verification, or error-terminal states are the blocker | `./references/turbine-flow-testing.md` |
| setting up test dependencies, Gradle configuration, or choosing libraries is the blocker | `./references/gradle-dependencies-and-config.md` |
| delay control, scheduler advancement, or dispatcher injection is the blocker | `./references/coroutine-test-determinism.md` |
| Flow replay semantics or bounded collection shape is the blocker | `./references/flow-testing.md` |
| JUnit 5 nested structure, grouped assertions, or timeout variants are the blocker | `./references/junit5-structure-and-timeouts.md` |
| mocking boundaries or MockK collaboration checks are the blocker | `./references/mocking-boundaries-and-mockk.md` |
| Kotest style, soft assertions, or exact exception checks are the blocker | `./references/kotest-style-and-exact-exceptions.md` |
| eventual consistency requires Awaitility rather than scheduler control or bounded collection | `./references/eventual-consistency-and-awaitility.md` |

## Core Decisions

### Start with `kotlin.test`

Use `@Test` and baseline assertions first.
Prefer the multi-assertion form that checks several properties in one test body.

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertContentEquals
import kotlin.test.assertContains

class ProfileServiceTest {
    @Test
    fun returnsCachedProfile() {
        val result = service.loadProfile("user-1")
        assertEquals(Profile("user-1"), result)
        assertTrue(result.isActive)
        assertNull(result.error)
    }

    @Test
    fun returnsTagList() {
        val tags = service.loadTags("user-1")
        assertContentEquals(listOf("admin", "editor"), tags)
        assertContains(tags, "admin")
    }
}
```

These are the common `kotlin.test` assertions you will reach for most often:

| Assertion | When to use it |
| --- | --- |
| `assertEquals(expected, actual)` | value equality is the contract |
| `assertTrue(condition)` / `assertFalse(condition)` | boolean predicate is the contract |
| `assertNull(value)` / `assertNotNull(value)` | nullability is part of the contract |
| `assertContentEquals(expected, actual)` | comparing lists, arrays, or sequences by element |
| `assertContains(collection, element)` / `assertContains(charSequence, value)` | membership check on collections or strings |
| `assertNotEquals(illegal, actual)` | proving a value is *not* something specific |
| `assertSame(expected, actual)` | referential identity (not equality) matters |
| `assertFailsWith<T> { ... }` | thrown type and message/properties are the contract |
| `fail(reason)` | mark an unreachable branch as a test failure |

Layer JUnit 5 annotations such as `@DisplayName`, `@BeforeEach`, or `@ParameterizedTest` only when the suite already uses Jupiter features.

Import rule: When using any JUnit 5 feature (`@Nested`, `@ParameterizedTest`, `@DisplayName`, `@TempDir`, etc.), import `@Test` from `org.junit.jupiter.api.Test`.
When using only `kotlin.test` features, import `@Test` from `kotlin.test.Test`.
Never mix both imports in the same file -- the compiler cannot resolve which `@Test` you mean.

### Use `runTest` for suspend code

`runTest` is the ordinary path for coroutine-aware tests.
It skips delays and surfaces uncaught child-coroutine failures.

When code under test uses `withTimeout`, a timed-out `delay` inside `runTest` throws `TimeoutCancellationException` (a subclass of `CancellationException`).
Since `runTest` handles `CancellationException` at scope level, timeout assertions work naturally.
Start the timed operation first, advance virtual time, then flush the scheduler before awaiting the result:

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Test
fun returnsFallbackOnTimeout() = runTest {
    val result = async { service.loadWithTimeout(OrderId("1")) }
    advanceTimeBy(5_000)
    runCurrent()
    assertEquals(Fallback, result.await())
}
```

Do not wrap `runTest` bodies in try/catch for `CancellationException` or `TimeoutCancellationException` -- `runTest` manages cancellation lifecycle automatically.

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class OrderSummaryServiceTest {
    @Test
    fun loadsOrderSummary() = runTest {
        val result = service.loadSummary(OrderId("o-1"))
        assertEquals(OrderSummary("o-1"), result)
    }
}
```

### Keep Flow collection bounded

Use `first()`, `single()`, or `take(n).toList()` to prove a finite contract and finish the test.

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class UiStateRepositoryTest {
    @Test
    fun emitsLoadingThenData() = runTest {
        val items = repository.observe().take(2).toList()
        assertTrue(items.size == 2)
        assertEquals(UiState.Loading, items[0])
        assertEquals(UiState.Data, items[1])
    }
}
```

### Use direct exception assertions

Use `assertFailsWith<T>()` when the thrown type is part of the contract.
It returns the exception, so message or property checks can stay explicit.

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetryPolicyTest {
    private val service = RetryPolicyService()

    @Test
    fun rejectsInvalidRetryBudget() {
        val error = assertFailsWith<IllegalArgumentException> {
            service.configure(-1)
        }
        assertEquals("retry budget must be non-negative", error.message)
    }
}
```

In suites that already use Kotest, use `shouldThrowExactly<T>()` for the same contract because it rejects subclasses of `T`.
Assert `error.message shouldBe "<exact expected text>"` plus the meaningful fields the exception declares, and use `shouldNotThrowAny { }` only when no-exception is itself the contract.
Open the Kotest reference for the exact shapes and the `assertSoftly` interaction caveat.

## Completion

Run the affected native tests and fix failures caused by the authorized change.
Report the behavior proved, command and result, and any unverified boundary.
Review only relevant coroutine, Flow, exception, or mocking decisions.
Do not require every pattern for each test.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| asserting internal call order instead of behavior | the test couples to implementation noise | assert the visible contract first |
| using real delays in coroutine tests | the suite becomes slow and flaky | use `runTest` and scheduler control |
| collecting a Flow forever | the test never reaches a bounded assertion | use `first()`, `single()`, or `take(n).toList()` |
| over-mocking simple values or pure helpers | fixtures become harder to read than the code under test | keep simple values real |
| reaching for framework-specific helpers before a plain test works | the test shape becomes heavier than the behavior | start with `kotlin.test` and grow only when needed |
| using `assertEquals` on lists when element order is unstable | structural comparison fails on reorderings | use `assertContains` or sort before `assertEquals` |

## Scope Boundaries

Use this skill for Kotlin JVM unit and integration test shape, coroutine-aware test execution, bounded Flow assertions, and practical mocking-boundary choices.
This skill covers JVM testing with JUnit 5, MockK (JVM), Kotest, and Turbine.
For multiplatform Kotlin testing (`kotlin-test-js`, `kotlin-test-native`), adapt assertions to the target platform's available surface.

Coroutine API design, general Kotlin language refactors, and framework-heavy application-context testing such as Spring `@SpringBootTest` are adjacent domains outside this JVM test-shape scope.
