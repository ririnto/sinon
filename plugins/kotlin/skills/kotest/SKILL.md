---
metadata:
  reference:
    Kotlin:
      version: 2.4.20
      url: https://github.com/JetBrains/kotlin/releases/tag/v2.4.20
    Kotest:
      version: 6.2.5
      url: https://github.com/kotest/kotest/releases/tag/6.2.5
    Kotest documentation:
      version: 6.2
      url:
        - https://kotest.io/docs/framework/project-setup.html
        - https://kotest.io/docs/framework/testing-styles.html
        - https://kotest.io/docs/framework/lifecycle-hooks.html
        - https://kotest.io/docs/assertions/matchers.html
        - https://kotest.io/docs/assertions/core-matchers.html
        - https://kotest.io/docs/assertions/soft-assertions.html
        - https://kotest.io/docs/assertions/exceptions.html
    kotlinx.coroutines:
      - version: 1.7.0
        url: https://github.com/Kotlin/kotlinx.coroutines/blob/1.7.0/CHANGES.md
      - version: 1.11.0
        url: https://github.com/Kotlin/kotlinx.coroutines/tree/1.11.0/kotlinx-coroutines-test
    Kotest timeout documentation:
      version: 6.2
      url: https://kotest.io/docs/framework/concurrency6.html
    MockK:
      version: 1.14.11
      url: https://github.com/mockk/mockk
    Turbine:
      version: 1.2.1
      url: https://github.com/cashapp/turbine/blob/1.2.1/README.md
    Kotest coroutine dispatcher:
      version: 6.2
      url: https://kotest.io/docs/framework/coroutines/test-coroutine-dispatcher.html
    Kotest eventual assertions:
      version: 6.2
      url: https://kotest.io/docs/assertions/eventually.html
    Kotest extensions:
      version: 6.2
      url:
        - https://kotest.io/docs/framework/extensions/extensions-introduction.html
        - https://kotest.io/docs/framework/extensions/simple-extensions.html
        - https://kotest.io/docs/framework/extensions/advanced-extensions.html
        - https://kotest.io/docs/framework/extensions/extension-examples.html
        - https://kotest.io/docs/extensions/spring.html
        - https://kotest.io/docs/extensions/allure.html
        - https://kotest.io/docs/extensions/blockhound.html
        - https://kotest.io/docs/extensions/test_clock.html
        - https://kotest.io/docs/extensions/decoroutinator.html
        - https://kotest.io/docs/extensions/html_reporter.html
        - https://kotest.io/docs/extensions/instant.html
        - https://kotest.io/docs/extensions/junit_xml.html
        - https://kotest.io/docs/extensions/koin.html
        - https://kotest.io/docs/extensions/ktor.html
        - https://kotest.io/docs/extensions/mockserver.html
        - https://kotest.io/docs/extensions/pitest.html
        - https://kotest.io/docs/extensions/system_extensions.html
        - https://kotest.io/docs/extensions/test_containers.html
        - https://kotest.io/docs/extensions/wiremock.html
    Kotest BOM:
      version: 6.2.5
      url: https://repo1.maven.org/maven2/io/kotest/kotest-bom/6.2.5/kotest-bom-6.2.5.pom
    Kotest extension and Ktor matcher source:
      tag: 6.2.5
      url:
        - https://github.com/kotest/kotest/tree/6.2.5/kotest-extensions
        - https://github.com/kotest/kotest/tree/6.2.5/kotest-assertions/kotest-assertions-ktor/src/commonMain/kotlin/io/kotest/assertions/ktor/client
    WireMock configuration:
      url: https://wiremock.org/docs/configuration/
    Ktor test application:
      url: https://ktor.io/docs/server-testing.html
    Spring Test transactions:
      url: https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/tx.html
    Spring embedded database:
      url: https://docs.spring.io/spring-framework/reference/data-access/jdbc/embedded-database-support.html
    Spring Boot test context:
      url:
        - https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html
        - https://docs.spring.io/spring-boot/api/java/org/springframework/boot/SpringBootConfiguration.html
    Kotest isolation modes:
      version: 6.2
      url: https://kotest.io/docs/framework/isolation-mode.html
name: kotest
description: >-
  Write or fix Kotlin JVM tests, including deterministic coroutine, Flow, and exception-contract tests.
---

# Kotest

## Goal

Write clear, deterministic Kotlin tests by proving one observable behavior with the smallest scope that works.

Examples follow the documented APIs for Kotlin 2.4.20, Kotest 6.2.5, `kotlinx-coroutines-test` 1.11.0, MockK 1.14.11, and Turbine 1.2.1.
These are reference versions, not install targets.
Use existing managed versions unless a required feature justifies an authorized dependency change.
When choosing a new version, check Maven Central for the latest stable version compatible with the project's Kotlin, Kotest, and other libraries, then record it in the project catalog.
This skill covers JVM testing only -- for multiplatform targets, adapt the test framework and assertions to each target.
Keep the common path centered on Kotest suspending test bodies, bounded Flow collection, and direct exception assertions.
Use blocker references only when virtual time, replay semantics, mocking-library details, or test organization and timeouts become the real problem.

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
- SHOULD use Kotest `FunSpec` and matchers as the default surface for new JVM test suites.
- MUST enable Kotest `coroutineTestScope` when virtual scheduler control is required.
  Plain suspending tests need no wrapper.
- SHOULD use `first()` for one emission and `take(n).toList()` for a known prefix.
  Use `single()` only when the Flow completes after one element.
- MUST use `shouldThrowExactly<T>()` for exception assertions to require the exact type.
- MUST assert the caught exception's message and meaningful fields when the exception contract matters, not only the type.
- MUST NOT migrate an existing suite or replace its native library-specific harness unless the task authorizes that change.
- MUST compare serialized output with full equality after parsing structured formats into exact fields or elements.
  - Use containment only when membership itself is the observable contract.
- MUST avoid real sleeps when deterministic scheduler control can prove the same behavior.
- SHOULD keep mocks at collaboration boundaries and keep simple values real.

## Task Context

Read the production contract and related tests before choosing test scope.
Use Kotest for new JVM test suites, and preserve an existing suite's runner unless the task authorizes migration.
Reuse existing assertions and fixtures when extending that suite.
Use Kotest suspending test bodies directly.
Enable `coroutineTestScope` only when virtual time or a test dispatcher is required.
Use the reference table below for the test behavior or execution problem under change.

## References

Read the references that match the current decision.

| Open when... | Read... |
| --- | --- |
| step-by-step Flow inspection, cancellation verification, or error-terminal states are the blocker | `./references/turbine-flow-testing.md` |
| setting up test dependencies, Gradle configuration, or choosing libraries is the blocker | `./references/gradle-dependencies-and-config.md` |
| delay control, scheduler advancement, or dispatcher injection is the blocker | `./references/coroutine-test-determinism.md` |
| Flow replay semantics or bounded collection shape is the blocker | `./references/flow-testing.md` |
| nested test structure, grouped assertions, or timeouts are the blocker | `./references/kotest-structure-and-timeouts.md` |
| choosing FunSpec, StringSpec, FreeSpec, or another supported spec style is the blocker | `./references/spec-styles.md` |
| selecting an extension, its lifecycle, or registration scope is the blocker | `./references/extensions.md` |
| Koin, Testcontainers, WireMock, or MockServer integration is the blocker | `./references/service-integration-extensions.md` |
| system properties, standard output, injected clocks, or static `now()` calls are the blocker | `./references/environment-and-time-extensions.md` |
| Allure, XML/HTML reports, BlockHound, Decoroutinator, Pitest, or Ktor matchers are the blocker | `./references/tooling-extensions.md` |
| Spring integration, constructor injection, test transactions, or Spring callbacks are the blocker | `./references/spring-extension.md` |
| mocking boundaries or MockK collaboration checks are the blocker | `./references/mocking-boundaries-and-mockk.md` |
| Kotest style, soft assertions, or exact exception checks are the blocker | `./references/kotest-style-and-exact-exceptions.md` |
| external eventual consistency requires polling rather than virtual time or bounded Flow collection | `./references/eventual-consistency.md` |

## Core Decisions

### Start with Kotest

Use `FunSpec` and Kotest matchers for new JVM test suites.
Use `assertSoftly` when several assertions describe one observable behavior.

```kotlin
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ProfileServiceTest : FunSpec({
    test("returns cached profile") {
        assertSoftly(service.loadProfile("user-1")) {
            shouldBe(Profile("user-1"))
            id shouldBe "user-1"
            isActive.shouldBeTrue()
            error.shouldBeNull()
        }
    }

    test("returns tags in contract order") {
        service.loadTags("user-1") shouldContainExactly listOf("admin", "editor")
    }
})
```

Use these common matchers:

| Matcher | When to use it |
| --- | --- |
| `actual shouldBe expected` | value equality is the contract |
| `actual shouldNotBe unexpected` | a specific value must not match |
| `actual.shouldBeTrue()` / `actual.shouldBeFalse()` | a boolean predicate is the contract |
| `actual.shouldBeNull()` / `actual.shouldNotBeNull()` | nullability is part of the contract |
| `actual shouldContainExactly expected` | ordered collection contents are the contract |
| `actual shouldContain item` | membership is the contract |
| `actual shouldBeInstanceOf<Type>()` | runtime type is the contract |
| `shouldThrowExactly<Type> { ... }` | exact exception type and its fields are the contract |

Use Kotest styles to group related test cases, and use descriptive test names instead of annotation-based display labels.
Use Kotest lifecycle hooks for fixture setup and cleanup.

### Use suspending tests directly

Kotest `FunSpec` test bodies are suspending.
Enable `coroutineTestScope` for virtual-time control.
Kotest runs those tests with a test dispatcher.

When `withTimeout` and `delay` must run against virtual time, enable `coroutineTestScope` for that test.
Start the timed operation first, advance virtual time, then flush the scheduler before awaiting the result:

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import io.kotest.core.test.testCoroutineScheduler

class OrderServiceTest : FunSpec({
    test("returns fallback on timeout").config(coroutineTestScope = true) {
        val result = async { service.loadWithTimeout(OrderId("1")) }
        testCoroutineScheduler.advanceTimeBy(5_000)
        testCoroutineScheduler.runCurrent()
        result.await() shouldBe Fallback
    }
})
```

Assert the observable timeout behavior without catching cancellation to make a test pass.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OrderSummaryServiceTest : FunSpec({
    test("loads order summary") {
        service.loadSummary(OrderId("o-1")) shouldBe OrderSummary("o-1")
    }
})
```

### Keep Flow collection bounded

Use `first()`, `single()`, or `take(n).toList()` to prove a finite contract and finish the test.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList

class UiStateRepositoryTest : FunSpec({
    test("emits loading then data") {
        repository.observe().take(2).toList() shouldContainExactly listOf(UiState.Loading, UiState.Data)
    }
})
```

### Use direct exception assertions

Use `shouldThrowExactly<T>()` for exception assertions to require the exact type.
It returns the exception, so message and property checks stay explicit.

```kotlin
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RetryPolicyTest : FunSpec({
    test("rejects invalid retry budget") {
        shouldThrowExactly<IllegalArgumentException> {
            service.configure(-1)
        }.message shouldBe "retry budget must be non-negative"
    }
})
```

Assert the exact message and meaningful exception fields when they are part of the contract.
Use `shouldNotThrowAny { }` only when no-exception is itself the contract.
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
| using real delays for a virtual-time contract | the suite becomes slow and flaky | enable Kotest `coroutineTestScope` |
| collecting a Flow forever | the test never reaches a bounded assertion | use `first()`, `single()`, or `take(n).toList()` |
| over-mocking simple values or pure helpers | fixtures become harder to read than the code under test | keep simple values real |
| reaching for framework-specific helpers before a plain test works | the test shape becomes heavier than the behavior | start with a Kotest `FunSpec` and simple matchers |
| asserting unstable collection order | structural comparison fails on reorderings | assert membership or sort before `shouldBe` |

## Scope Boundaries

Use this skill for Kotlin JVM unit and integration test shape, coroutine-aware test execution, bounded Flow assertions, and practical mocking-boundary choices.
This skill covers Kotlin/JVM testing with Kotest, MockK, and Turbine.
Adapt multiplatform testing separately for each target.

Coroutine API design and general Kotlin language refactors are outside this test-shape scope.
Use the Spring extension reference only when a test requires the Spring application context.
