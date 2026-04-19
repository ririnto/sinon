---
title: Flow Replay and Bounded Assertions
description: >-
  Open this when Flow replay semantics or bounded collection shape is the blocker.
---

Use this reference when the job is to prove one Flow contract with bounded, deterministic assertions. This reference should be sufficient on its own for that task.

Use this file to finish one of these jobs:

- assert a finite prefix of a cold `Flow`
- verify the replay behavior of `StateFlow`
- verify that a `SharedFlow` with `replay = 0` only reaches active collectors
- keep collection bounded so the test finishes as soon as the contract is proven

Bounded Flow assertion:

```kotlin
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals

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

Use when: a cold `Flow` or bounded stream contract can be proven by collecting only the exact number of items needed.

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

Use when: the project already uses Kotest and the Flow assertion should match the existing test style.

StateFlow replay example:

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UiStateReplayTest {
    @Test
    fun newCollectorGetsLatestState() = runTest {
        val state = MutableStateFlow<UiState>(UiState.Loading)
        state.value = UiState.Data

        assertEquals(UiState.Data, state.first())
    }
}
```

Use when: the contract is about the latest replayed state rather than every intermediate transition.

SharedFlow replay-zero example:

```kotlin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UiEventReplayTest {
    @Test
    fun replayZeroRequiresActiveCollector() = runTest {
        val events = MutableSharedFlow<String>(replay = 0)
        val collected = async(start = CoroutineStart.UNDISPATCHED) { events.first() }

        yield()

        events.emit("saved")

        assertEquals("saved", collected.await())
    }
}
```

Use when: the contract depends on starting collection before emission because the stream should not replay old events.

StateFlow and SharedFlow rules:

- `StateFlow` tests should assert the latest replayed state unless the contract explicitly requires every intermediate step
- `SharedFlow` tests should start collection before emission when replay is `0`
- use bounded collection helpers such as `first()`, `single()`, or `take(n).toList()` instead of collecting forever

Replay-focused checklist:

1. Name the expected replay behavior before writing assertions.
2. Keep collection bounded to the exact items needed for the scenario.
3. Cancel the collector or finish the scope once the contract is proven.

The JUnit examples in this file use JUnit consistently; the Kotest example is the deliberate exception for Kotest-based suites.
