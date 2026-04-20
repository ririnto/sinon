---
title: Gradle Dependencies and Test Configuration
description: >-
  Open this when setting up a Kotlin test suite from scratch, adding a new testing library,
  or configuring Gradle test execution.
---

Open this when declaring test dependencies, configuring the Gradle test task, or setting up a new module for testing.

## Core dependencies

```kotlin
dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:${kotlinVersion}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.mockk:mockk")
}
```

Optional libraries that may be added based on project needs: `kotest-runner-junit5`, `turbine`, `awaitility-kotlin`, `junit-jupiter` (Testcontainers).

Pin versions through a `libs.versions.toml` catalog or the project's dependency management strategy rather than hardcoding them here. The libraries above resolve their latest compatible versions from Maven Central at configuration time.

## Test task configuration

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
| Baseline assertions + JUnit 5 runner | `kotlin.test` + `kotlin-test-junit5` | `testImplementation(kotlin("test"))` |
| Coroutine test control | `kotlinx-coroutines-test` | see above |
| Mocking | MockK | `io.mockk:mockk` |
| Rich matchers + spec styles | Kotest | `io.kotest:kotest-runner-junit5` |
| Step-by-step Flow inspection | Turbine | `app.cash.turbine:turbine` |
| Polling / eventual consistency | Awaitility | `org.awaitility:awaitility-kotlin` |
| Docker-based integration tests | Testcontainers | `org.testcontainers:*` |

## Rules

- declare test dependencies with `testImplementation`, never `implementation`
- pin versions for transitive-heavy libraries (MockK, Kotest, coroutines-test)
- include `junit-platform-launcher` as `testRuntimeOnly` when using JUnit 5
- configure `maxParallelForks` only after confirming tests have no shared mutable state
