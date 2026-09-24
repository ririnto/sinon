---
description: >-
  Open this when Flow replay semantics or bounded collection shape is the blocker.
---

# Flow Replay and Bounded Assertions

Prove one Flow contract with bounded assertions:

- assert a finite prefix of a cold `Flow`
- verify the replay behavior of `StateFlow`
- verify that a `SharedFlow` with `replay = 0` only reaches active collectors
- keep collection bounded so the test finishes as soon as the contract is proven

Kotest Flow matcher shape:

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList

class UiStateRepositoryKotestTest : FunSpec({
    test("emits loading then data") {
        repository.observe().take(2).toList() shouldContainExactly listOf(UiState.Loading, UiState.Data)
    }
})
```

StateFlow replay example:

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class UiStateReplayTest : FunSpec({
    test("a new collector gets the latest state") {
        val state = MutableStateFlow<UiState>(UiState.Loading)
        state.value = UiState.Data
        state.first() shouldBe UiState.Data
    }
})
```

Use when: the contract is about the latest replayed state rather than every intermediate transition.

SharedFlow replay-zero example:

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield

class UiEventReplayTest : FunSpec({
    test("replay zero requires an active collector") {
        coroutineScope {
            val events = MutableSharedFlow<String>(replay = 0)
            val collected = async(start = CoroutineStart.UNDISPATCHED) { events.first() }
            yield()
            events.emit("saved")
            collected.await() shouldBe "saved"
        }
    }
})
```

Use when: the contract depends on starting collection before emission because the stream should not replay old events.

StateFlow and SharedFlow rules:

- `StateFlow` tests should assert the latest replayed state unless the contract explicitly requires every intermediate step
- `SharedFlow` tests should start collection before emission when replay is `0`
- use `first()` for one emission or `take(n).toList()` for an expected prefix of a non-completing Flow
- use `single()` only when the Flow completes after one element

Replay-focused checklist:

1. Name the expected replay behavior before writing assertions.
2. Keep collection bounded to the exact items needed for the scenario.
3. Cancel the collector or finish the scope once the contract is proven.

These examples use Kotest `FunSpec` and matchers as the default test style.
For exact-exception assertions, use the `shouldThrowExactly` shape documented in `kotest-style-and-exact-exceptions.md`.
