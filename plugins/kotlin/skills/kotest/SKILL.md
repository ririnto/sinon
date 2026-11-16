---
name: kotest
description: >-
  Write or fix Kotlin JVM tests with Kotest, including coroutine, Flow, and exception-contract tests.
---

# Kotest

## Goal

Write clear, deterministic Kotlin tests by proving one observable behavior with the smallest scope that works.

Examples use Kotlin 2.4.20, Kotest 6.2.5, `kotlinx-coroutines-test` 1.11.0, MockK 1.14.11, and Turbine 1.2.1.
These are reference versions, not install targets.
Use existing managed versions unless a required feature justifies an authorized dependency change.
When choosing a new version, check Maven Central for the latest stable version compatible with the project's Kotlin, Kotest, and other libraries, then record it in the project catalog.
For multiplatform targets, adapt the test framework and assertions to each target; this skill covers JVM testing.
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
- SHOULD choose the spec style for the test's structure and declare tests through its constructor DSL.
- Follow the target project's type declaration conventions.
- SHOULD keep shared immutable class-level test configuration in a typed companion object property.
- MUST enable Kotest `coroutineTestScope` when virtual scheduler control is required.
  Plain suspending tests need no wrapper.
- SHOULD use `first()` for one emission and `take(n).toList()` for a known prefix.
  Use `single()` only when the Flow completes after one element.
- Use `shouldThrowExactly<T>()` when the exact exception type is part of the contract.
  Use `shouldThrow<T>()` when a subtype also satisfies it.
- MUST assert the caught exception's message and meaningful fields when the exception contract matters, not only the type.
- MUST NOT migrate an existing suite or replace its native library-specific harness unless the task authorizes that change.
- MUST compare serialized output with full equality after parsing structured formats into exact fields or elements.
  - Use containment only when membership itself is the observable contract.
- MUST avoid real sleeps when deterministic scheduler control can prove the same behavior.
- SHOULD keep mocks at collaboration boundaries and keep simple values real.

## Task Context

Read the production contract and related tests before choosing scope.
Use Kotest for new JVM test suites, and preserve an existing suite's runner unless the task authorizes migration.
Reuse assertions and fixtures when extending it.
Use suspending test bodies directly.
Enable `coroutineTestScope` only when virtual time or a test dispatcher is required.
Use the table for the problem.

## References

The [source ledger](references/sources.md) covers this skill and its references.

Read matching references.

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

Use styles to group related tests, descriptive names instead of display annotations, and lifecycle hooks. Use `assertSoftly` when several assertions describe one observable behavior.
Use suspending tests directly; enable `coroutineTestScope` for virtual time, start timed work before advancing time, flush the scheduler, and assert the outcome without catching cancellation.
Use `shouldNotThrowAny` only when no exception is the contract.
For examples, matcher choices, and the `assertSoftly` exception interaction, read [core decisions](references/core-decisions.md).

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

Coroutine API design and general Kotlin language refactors are outside this test-shape scope.
Use the Spring extension reference only when a test requires the Spring application context.
