---
name: kotlin-test
description: >-
  Write clear, deterministic Kotlin tests that prove one observable behavior with the smallest correct scope. Use this skill when the user asks to "test Kotlin code", "write a coroutine test", "mock a Kotlin dependency", "structure Kotlin tests", or needs guidance on practical Kotlin testing patterns.
---

# Kotlin Test

## Goal

Write clear, deterministic Kotlin tests by proving one observable behavior with the smallest scope that works.

**Minimum Kotlin version: 1.9** -- examples use `kotlin.test` baseline assertions, `kotlinx.coroutines.test` (1.7+), JUnit 5 Jupiter APIs, MockK 1.14+, Kotest 6.x, and Turbine 1.2+. All library versions are managed through the project's dependency catalog; pin versions when adopting features from specific releases. This skill covers JVM testing only -- for multiplatform targets, adapt assertions to `kotlin-test-js` or `kotlin-test-native`. Keep the common path centered on `kotlin.test`, `runTest` for suspend code, bounded Flow collection, and direct exception assertions; use blocker references only when virtual time, replay semantics, mocking-library details, or JUnit 5 structure features become the real problem.

## Operating Rules

- MUST choose the smallest test scope that proves the behavior.
- SHOULD keep one observable behavior per test.
- SHOULD name tests as `verbCondition` or `subjectVerb` describing the observable behavior (e.g., `returnsCachedProfile`, `emitsLoadingThenData`, `rejectsInvalidInput`).
- SHOULD use `kotlin.test` annotations and baseline assertions as the default surface.
- MUST use `runTest` when coroutine semantics actually matter.
- SHOULD keep Flow assertions bounded with `first()`, `single()`, or `take(n).toList()`.
- SHOULD use `assertFailsWith<T>()` when exception type is part of the contract.
- MUST avoid real sleeps when deterministic scheduler control can prove the same behavior.
- SHOULD keep mocks at collaboration boundaries and keep simple values real.

## Common-Path Procedure

1. Read the production behavior and the nearest related tests first.
2. Choose the smallest test scope that proves one observable contract.
3. Start with `kotlin.test` assertions and plain synchronous structure.
4. Add `runTest` only when the code under test uses `suspend`, delay, cancellation, or Flow collection, and make sure `kotlinx-coroutines-test` is available in test scope.
5. Bound Flow collection to the exact items needed for the assertion.
6. Layer JUnit 5 structure or other test libraries only when the suite already uses them.
7. Open one blocker reference only when scheduler control, replay semantics, JUnit 5 structure, MockK, Kotest, or eventual-consistency helpers are the actual blocker.

## Core Decisions

### Start with `kotlin.test`

Use `@Test` and baseline assertions first. Prefer the multi-assertion form that checks several properties in one test body.

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

Import rule: When using any JUnit 5 feature (`@Nested`, `@ParameterizedTest`, `@DisplayName`, `@TempDir`, etc.), import `@Test` from `org.junit.jupiter.api.Test`. When using only `kotlin.test` features, import `@Test` from `kotlin.test.Test`. Never mix both imports in the same file -- the compiler cannot resolve which `@Test` you mean.

### Use `runTest` for suspend code

`runTest` is the ordinary path for coroutine-aware tests. It skips delays and surfaces uncaught child-coroutine failures.

When code under test uses `withTimeout`, a timed-out `delay` inside `runTest` throws `TimeoutCancellationException` (a subclass of `CancellationException`). Since `runTest` handles `CancellationException` at scope level, timeout assertions work naturally. Start the timed operation first, advance virtual time, then flush the scheduler before awaiting the result:

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

Use `assertFailsWith<T>()` when the thrown type is part of the contract. It returns the exception, so message or property checks can stay explicit.

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

## First Safe Default

If the path is still unclear, write one synchronous behavior test first, then add `runTest` or bounded Flow collection only if the production contract requires it.

## Validate the Result

Check these pass/fail conditions before you stop:

- the test proves one observable behavior rather than internal choreography
- coroutine tests use `runTest` instead of real sleeps
- Flow tests collect only the amount needed for the assertion
- exception assertions prove the exact contract that matters
- mocks exist only at real collaboration boundaries
- assertion choice matches the contract shape (equality vs content-equality vs containment)

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| asserting internal call order instead of behavior | the test couples to implementation noise | assert the visible contract first |
| using real delays in coroutine tests | the suite becomes slow and flaky | use `runTest` and scheduler control |
| collecting a Flow forever | the test never reaches a bounded assertion | use `first()`, `single()`, or `take(n).toList()` |
| over-mocking simple values or pure helpers | fixtures become harder to read than the code under test | keep simple values real |
| reaching for framework-specific helpers before a plain test works | the test shape becomes heavier than the behavior | start with `kotlin.test` and grow only when needed |
| using `assertEquals` on lists when element order is unstable | structural comparison fails on reorderings | use `assertContains` or sort before `assertEquals` |

## Output Contract

Return:

1. the chosen test scope and the behavior it proves
2. whether the test stays synchronous, uses `runTest`, or uses bounded Flow collection
3. any exception or mocking decisions that affect the contract
4. any blocker references needed for deeper branches

## Blocker References

Open only the reference that matches the remaining blocker.

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

## Scope Boundaries

Use this skill for Kotlin JVM unit and integration test shape, coroutine-aware test execution, bounded Flow assertions, and practical mocking-boundary choices. This skill covers JVM testing with JUnit 5, MockK (JVM), Kotest, and Turbine. For multiplatform Kotlin testing (`kotlin-test-js`, `kotlin-test-native`), adapt assertions to the target platform's available surface.

Coroutine API design, general Kotlin language refactors, and framework-heavy application-context testing such as Spring `@SpringBootTest` are adjacent domains outside this JVM test-shape scope.
