---
description: >-
  Open this when setting up a Kotlin test suite from scratch, adding a new testing library, or configuring Gradle test execution.
---

# Gradle Dependencies and Test Configuration

Open this when declaring test dependencies, configuring the Gradle test task, or setting up a new module for testing.

Reuse the existing native test harness and managed versions before considering dependency changes.
The examples are alternatives for an authorized setup task, not a required library bundle.

## Core dependencies

```kotlin
dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.4.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.mockk:mockk:1.14.11")
}
```

Optional libraries that may be added based on project needs: `kotest-runner-junit5`, `turbine`, `awaitility-kotlin`, `junit-jupiter` (Testcontainers).

Pin versions through a `libs.versions.toml` catalog or the project's dependency management strategy.
Check example versions against the project baseline before adoption.
Update versions deliberately when the module's Kotlin baseline changes.

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
| Baseline assertions + JUnit 5 runner | `kotlin.test` + `kotlin-test-junit5` | `org.jetbrains.kotlin:kotlin-test-junit5:2.4.0` |
| Coroutine test control | `kotlinx-coroutines-test` | `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0` |
| Mocking | MockK | `io.mockk:mockk:1.14.11` |
| Rich matchers + spec styles | Kotest | `io.kotest:kotest-runner-junit5:6.2.2` |
| Step-by-step Flow inspection | Turbine | `app.cash.turbine:turbine:1.2.1` |
| Polling / eventual consistency | Awaitility | `org.awaitility:awaitility-kotlin:4.3.0` |
| Docker-based integration tests | Testcontainers | `org.testcontainers:*` |

## Rules

- declare test dependencies with `testImplementation`, never `implementation`
- pin versions for transitive-heavy libraries (MockK, Kotest, coroutines-test)
- include `junit-platform-launcher` as `testRuntimeOnly` when using JUnit 5
- configure `maxParallelForks` only after confirming tests have no shared mutable state
- adopt Kotest only for an application suite that already uses it.
  Tests for library internals, such as ktlint custom rules, use their own maintained harness (for example `KtLintAssertThat` from `com.pinterest.ktlint:ktlint-test`) instead of adding Kotest alongside it.
