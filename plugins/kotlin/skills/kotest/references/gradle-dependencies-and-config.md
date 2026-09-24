---
description: >-
  Open this when setting up a Kotlin test suite from scratch, adding a new testing library, or configuring Gradle test execution.
---

# Gradle Dependencies and Test Configuration

Open this when declaring test dependencies, configuring the Gradle test task, or setting up a new module for testing.

Reuse the existing native test harness and managed versions before considering dependency changes.
The examples are alternatives for an authorized setup task, not a required library bundle.
Before adding a library, check Maven Central for its latest stable version compatible with the project's Kotlin and other libraries.
Keep the project's existing version if it already satisfies the needed API.
Manage selected versions in the project catalog or dependency management.
Kotest's BOM covers Kotest modules, not MockK, Turbine, or `kotlinx-coroutines-test`.

## Core dependencies

This executable sample uses the Kotest 6.2.5 source baseline.
Check Maven Central and the project's compatibility policy before choosing a version for a new installation.

```kotlin
dependencies {
    testImplementation(platform("io.kotest:kotest-bom:6.2.5"))
    testImplementation("io.kotest:kotest-runner-junit5")
}
```

The Kotest JVM runner uses the JUnit Platform, so configure Gradle's test task with `useJUnitPlatform()`.
Add `kotlinx-coroutines-test` only when test code calls the scheduler API or directly constructs a test dispatcher.
Kotest provides `coroutineTestScope`, but its internal test dependency does not expose scheduler types on the consumer's compile classpath.
Add Turbine for detailed Flow event assertions.
Use Kotest `eventually` for external polling.

Pin independent libraries through a `libs.versions.toml` catalog or the project's dependency management strategy.
Do not copy the versions cited as source baselines in this skill as a current install recommendation.
Update versions deliberately when the module's Kotlin baseline or required APIs change.

## Test task configuration

Keep existing test-task settings unless execution setup is the blocker.
The logging, heap, and parallelism values below are examples, not defaults to install in every project.

```kotlin
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standard_out", "standard_error")
        showExceptions = true
        showStackTraces = true
        exceptionFormat = TestExceptionFormat.FULL
    }
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    jvmArgs("-Xmx512m")
}
```

## Choosing libraries

| Need | Library | Dependency |
| --- | --- | --- |
| Default JVM test framework, assertions, and JUnit Platform engine | Kotest | `io.kotest:kotest-runner-junit5` with a compatible `io.kotest:kotest-bom` |
| Direct scheduler calls or test-dispatcher injection | `kotlinx-coroutines-test` | `org.jetbrains.kotlinx:kotlinx-coroutines-test` with a separately managed version |
| Mocking, only when a boundary needs a mock | MockK | `io.mockk:mockk` with a separately managed version (no MockK BOM) |
| Step-by-step Flow inspection | Turbine | `app.cash.turbine:turbine` with a separately managed version |
| Polling / external eventual consistency | Kotest `eventually` | Already in Kotest assertions |
| Docker-based integration tests | Testcontainers | `org.testcontainers:*` |

## Rules

- declare test dependencies with `testImplementation`, never `implementation`
- pin versions for transitive-heavy libraries (MockK, Kotest, coroutines-test)
- configure the Gradle `test` task with `useJUnitPlatform()` for Kotest JVM execution
- configure `maxParallelForks` only after confirming tests have no shared mutable state
- use Kotest as the default for new JVM test suites, and preserve an existing suite's runner unless migration is authorized.
  Tests for library internals, such as ktlint custom rules, should use their maintained harness (for example `KtLintAssertThat` from `com.pinterest.ktlint:ktlint-test`).
