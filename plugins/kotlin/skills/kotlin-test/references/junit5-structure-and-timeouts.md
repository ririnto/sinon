---
title: JUnit 5 Structure and Timeouts
description: >-
  Open this when JUnit 5 nested structure, grouped assertions, or timeout variants are the blocker.
---

Open this when plain `kotlin.test` structure is no longer enough and the suite already uses Jupiter features.

## Use this file to finish one of these jobs

- group a scenario tree with `@Nested` and `inner` classes
- add `@DisplayName` when human-facing labels improve scanning
- choose between declarative `@Timeout` and preemptive timeout assertions

## Patterns

Nested scenario tree:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

class BillingPolicyTest {
    @Nested
    @DisplayName("when the invoice is overdue")
    inner class WhenInvoiceIsOverdue {
        @Test
        fun appliesLateFee() {
            assertEquals(Money("5.00"), service.lateFee())
        }
    }
}
```

Grouped assertions for one observable behavior:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.Assertions.assertAll

class OrderSummaryTest {
    @Test
    fun exposesExpectedSummary() {
        val result = service.loadSummary(OrderId("o-1"))

        assertAll(
            { assertEquals(OrderId("o-1"), result.id) },
            { assertEquals(2, result.itemCount) }
        )
    }
}
```

Declarative timeout when the contract is just a time budget:

```kotlin
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import org.junit.jupiter.api.Timeout

class FeedRefreshTest {
    @Test
    @Timeout(value = 500, unit = TimeUnit.MILLISECONDS)
    fun refreshFinishesWithinBudget() {
        service.refresh()
    }
}
```

Preemptive timeout only when aborting the work matters:

```kotlin
import kotlin.test.Test
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import java.time.Duration

class RemotePricingTest {
    @Test
    fun abortsSlowRefresh() {
        assertTimeoutPreemptively(Duration.ofMillis(200)) {
            service.refreshPrices()
        }
    }
}
```

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| adding JUnit 5 structure before a plain test works | the test shape grows faster than the behavior | start with `kotlin.test` and layer Jupiter features only when needed |
| using `assertTimeoutPreemptively` in thread-local-sensitive code | the assertion runs work on another thread | prefer declarative `@Timeout` unless preemption is the actual contract |

## Lifecycle hooks

Use `@BeforeEach` / `@AfterEach` for per-test fixture setup. Use `@BeforeAll` / `@AfterAll` (on `companion object`) for expensive one-time setup.

```kotlin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Path

class FileProcessorTest {
    private lateinit var processor: FileProcessor

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        processor = FileProcessor(tempDir)
    }

    @Test
    fun writesOutputFile() {
        processor.process("input.txt")
        assertTrue(tempDir.resolve("output.txt").toFile().exists())
    }
}
```

## Parameterized tests

Use `@ParameterizedTest` with `@MethodSource` for data-driven cases. Use `@CsvSource` for small inline datasets.

```kotlin
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.Arguments
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscountCalculatorTest {

    @ParameterizedTest
    @CsvSource(
        "100, 10, 90.0",
        "200, 20, 160.0",
        "50,  0,  50.0"
    )
    fun appliesDiscountCorrectly(original: Int, percent: Int, expected: Double) {
        assertEquals(expected, calculator.apply(original, percent))
    }

    companion object {
        @JvmStatic
        private fun edgeCases() = listOf(
            Arguments.of(0, 10, 0.0),
            Arguments.of(-100, 10, -100.0),
        )
    }

    @ParameterizedTest
    @MethodSource("edgeCases")
    fun handlesEdgeCases(price: Int, percent: Int, expected: Double) {
        assertEquals(expected, calculator.apply(price, percent))
    }
}
```

## Test instance lifecycle

By default JUnit 5 creates a new instance per method (`PER_METHOD`). Use `@TestInstance(Lifecycle.PER_CLASS)` when tests share expensive state.

```kotlin
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle

@TestInstance(Lifecycle.PER_CLASS)
class DatabaseIntegrationTest {
    private lateinit var connection: Connection

    @BeforeAll
    fun startDatabase() {
        connection = Database.startEmbedded()
    }

    @AfterAll
    fun stopDatabase() {
        connection.close()
    }

    @Test
    fun insertsRow() { /* ... */ }

    @Test
    fun queriesRow() { /* ... */ }
}
```
