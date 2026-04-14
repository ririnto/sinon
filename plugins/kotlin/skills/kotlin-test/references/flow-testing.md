---
title: Flow Testing Guide
description: >-
  Reference for bounded Flow assertions, replay-focused checks, and Kotlin test patterns for StateFlow and SharedFlow contracts.
---

Use this reference when the test already needs coroutine tooling and the remaining blocker is Flow replay or bounded collection shape.

Bounded Flow assertion:

```kotlin
import kotlin.test.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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

Kotest Flow matcher shape:

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class UiStateRepositoryKotestTest : FunSpec() {
    init {
        test("emits loading then data") {
            runTest {
                val items = repository.observe().take(2).toList()
                assertSoftly {
                    items[0] shouldBe UiState.Loading
                    items[1] shouldBe UiState.Data
                }
            }
        }
    }
}
```

StateFlow and SharedFlow rules:

- `StateFlow` tests should assert the latest replayed state unless the contract explicitly requires every intermediate step
- `SharedFlow` tests should start collection before emission when replay is `0`
- use bounded collection helpers such as `first`, `take`, or `toList()` instead of collecting forever

Replay-focused checklist:

1. Name the expected replay behavior before writing assertions.
2. Keep collection bounded to the exact items needed for the scenario.
3. Cancel the collector or finish the scope once the contract is proven.
