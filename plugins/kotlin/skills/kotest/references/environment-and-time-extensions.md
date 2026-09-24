---
description: >-
  Open this when tests must control JVM properties, intercept print calls, or model time.
---

# Environment and Time Extensions

Use the project's compatible Kotest BOM for the modules below.
Verify each module is covered by the selected BOM.
Install only the one needed by the test.

```kotlin
dependencies {
    testImplementation("io.kotest:kotest-extensions-jvm")
    testImplementation("io.kotest:kotest-extensions")
    testImplementation("io.kotest:kotest-extensions-now")
}
```

These are alternatives, not a required bundle.

## JVM system properties and output

`SystemPropertyTestListener` snapshots all JVM properties when constructed, applies the override before a test, and restores that snapshot afterward.
Use `OverrideMode.SetOrOverride` when the key may already exist.
The default `SetOrError` fails on an existing key.
`NoSystemOutListener` and `NoSystemErrListener` intercept their `print(...)` overloads during a test.
They do not reliably reject a no-argument `println()` or direct `write(...)`.
Do not use them to prove that all output is forbidden.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.system.OverrideMode
import io.kotest.extensions.system.SystemPropertyTestListener
import io.kotest.matchers.booleans.shouldBeTrue

class FeaturePropertyTest : FunSpec({
    extension(SystemPropertyTestListener(mapOf("feature.enabled" to "true"), OverrideMode.SetOrOverride))
    test("reads the enabled feature") {
        System.getProperty("feature.enabled").toBooleanStrict().shouldBeTrue()
    }
})
```

For one block, use `withSystemProperty("feature.enabled", "true", OverrideMode.SetOrOverride) { ... }` from `io.kotest.extensions.system`.
Neither approach isolates process-global state across parallel specs.
Do not run tests that mutate the same JVM property concurrently.
Prefer injecting configuration into production code.

## Inject a controllable clock

`TestClock` implements `java.time.Clock`.
Inject it when production accepts a clock.
`TestClock.plus` and `minus` take **Kotlin** `Duration`, while its `instant()` returns `java.time.Instant`.
The clock is mutable and has millisecond precision.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.clock.TestClock
import io.kotest.matchers.shouldBe
import java.time.Instant
import kotlin.time.Duration.Companion.days

class ClockTest : FunSpec({
    test("advances an injected clock") {
        val clock = TestClock.utc(Instant.parse("2026-01-01T00:00:00Z"))
        clock.plus(1.days)
        clock.instant() shouldBe Instant.parse("2026-01-02T00:00:00Z")
    }
})
```

## Override static `now()` only at the boundary

`withConstantNow` intercepts `java.time` static `now()` calls and restores them in `finally`.
Use it only when production cannot accept an injected `Clock`.
`ConstantNowTestListener` provides test-scoped registration and `ConstantNowProjectListener` provides project-scoped registration.
These static overrides affect the entire JVM and are unsafe alongside parallel tests that read the same time type.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class CurrentDateTest : FunSpec({
    test("uses a fixed current date") {
        withConstantNow(LocalDate.of(2026, 1, 1)) {
            LocalDate.now() shouldBe LocalDate.of(2026, 1, 1)
        }
    }
})
```
