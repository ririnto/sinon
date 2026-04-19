---
name: kotlin-coroutines-flows
description: >-
  Use this skill when the user asks to "use coroutines", "design a suspend API", "choose Flow vs suspend", "debug cancellation", "review Kotlin async code", or needs guidance on Kotlin coroutine and Flow patterns.
metadata:
  title: "Kotlin Coroutines and Flows"
  official_project_url: "https://kotlinlang.org/docs/coroutines-overview.html"
  reference_doc_urls:
    - "https://kotlinlang.org/docs/coroutines-basics.html"
    - "https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html"
    - "https://kotlinlang.org/docs/cancellation-and-timeouts.html"
    - "https://kotlinlang.org/docs/exception-handling.html"
    - "https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html"
    - "https://kotlinlang.org/docs/flow.html"
    - "https://kotlinlang.org/docs/flow.html#stateflow-and-sharedflow"
    - "https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/"
    - "https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/"
    - "https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/"
    - "https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-shared-flow/"
    - "https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-mutex/"
---

## Goal

Design Kotlin coroutine and Flow code with honest async semantics, explicit ownership, and cancellation-safe behavior. Start with the smallest shape that matches the contract, then open a blocker reference only when scope, failure behavior, hot sharing, or concurrent mutation becomes the real problem.

## Operating Rules

- MUST prefer `suspend` for one logical async result.
- MUST use `Flow` only when the contract delivers values over time.
- MUST keep coroutine ownership explicit through a caller, parent function, or injected `CoroutineScope`.
- MUST let `CancellationException` propagate.
- MUST keep blocking or CPU-heavy boundaries explicit with the right dispatcher or context hop.
- SHOULD treat ordinary `Flow` as cold and sequential unless sharing or buffering is chosen intentionally.
- SHOULD choose `StateFlow` for current state and `SharedFlow` for events or broadcasts.
- MUST avoid detached work unless the API is explicitly about background ownership.

## Common-Path Procedure

1. Decide whether the caller needs one result, a stream over time, or explicitly owned background work.
2. Start with `suspend` for one-shot work and switch to `Flow` only if the contract is truly streaming.
3. Make ownership visible by keeping child work inside the current suspend function or launching from an explicit parent scope.
4. Keep cancellation and failure semantics boring by default: child failure cancels the structured parent unless supervision is intentionally required.
5. Keep dispatcher changes at real blocking or CPU-heavy boundaries rather than scattering them across leaf code.
6. Escalate to a blocker reference only when hot sharing, buffering, supervision, or shared mutable state is the actual hard part.

## Key Decisions

### `suspend` vs `Flow`

Choose `suspend` when the operation produces one logical answer and then completes.

```kotlin
suspend fun loadOrder(orderId: OrderId): Order = repository.load(orderId)
```

Choose `Flow` when the contract is ongoing observation, repeated updates, or incremental delivery over time.

```kotlin
fun observeOrders(): Flow<List<Order>> = repository.observeOrders()
```

### Cold `Flow` vs hot state or event streams

Use ordinary `Flow` as the default streaming type. It is usually cold, so each collection starts the upstream work again unless you share it intentionally.

Use `StateFlow` when every collector should immediately see the latest state.

```kotlin
private val _uiState = MutableStateFlow(UiState.Loading)
val uiState: StateFlow<UiState> = _uiState
```

Use `SharedFlow` when the stream represents events or broadcasts and replay must be chosen explicitly.

```kotlin
private val _events = MutableSharedFlow<UiEvent>(replay = 0)
val events: SharedFlow<UiEvent> = _events
```

### Ownership and dispatchers

Keep launched work attached to a visible owner.

```kotlin
class OrdersPresenter(private val presenterScope: CoroutineScope) {
    fun refresh() {
        presenterScope.launch {
            repository.refresh()
        }
    }
}
```

Keep blocking boundaries explicit.

```kotlin
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CsvImporter {
    suspend fun import(path: Path): ImportResult = withContext(Dispatchers.IO) {
        parser.import(path)
    }
}
```

## First Safe Default

If you are unsure, start here:

```kotlin
class OrderLoader(private val repository: OrderRepository) {
    suspend fun load(orderId: OrderId): Order = repository.load(orderId)
}
```

Only add `Flow`, extra scopes, sharing, or buffering when the contract clearly needs them.

## Validate the Result

Check these pass/fail conditions before you stop:

- one-shot work uses `suspend` instead of a decorative stream
- stream APIs describe real ongoing delivery rather than single-response work
- `StateFlow` and `SharedFlow` are chosen for clear state or event semantics
- scope ownership is visible and launched work is not detached by accident
- blocking or CPU-heavy work is not hidden inside an apparently cheap async path
- cancellation is preserved instead of swallowed in broad exception handling

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| returning `Flow` for a single result | the API looks reactive without changing the contract | use `suspend` |
| launching work without a visible owner | lifecycle and cancellation become ambiguous | attach work to an explicit parent scope |
| swallowing `CancellationException` | structured cancellation silently breaks | let cancellation propagate and clean up in `finally` |
| sharing or buffering a flow by default | delivery semantics become harder to reason about | keep the flow cold and sequential until sharing is required |
| mutating shared state from multiple coroutines without a rule | race conditions become hidden design bugs | confine the state or protect it deliberately |

## Output Contract

Return:

1. the chosen async shape and why it matches the contract
2. the ownership and cancellation model
3. any dispatcher, sharing, or buffering decisions that affect behavior
4. any blocker references needed for deeper branches

## Blocker References

Open these only when the named blocker is the real issue.

| Open this when... | Read... |
| --- | --- |
| you need `coroutineScope`, `supervisorScope`, explicit launch ownership, or dispatcher boundaries | `./references/scope-ownership-and-dispatchers.md` |
| you are debugging cancellation, timeouts, failure propagation, or cleanup semantics | `./references/cancellation-timeouts-and-failures.md` |
| you need to justify `Flow`, choose `StateFlow` or `SharedFlow`, or shape hot sharing and buffering | `./references/flow-selection-hot-sharing-and-buffering.md` |
| you are coordinating mutable state across coroutines and need a concurrency rule | `./references/shared-state-and-concurrency.md` |

## Scope Boundaries

Use this skill for coroutine structure, `suspend` versus `Flow`, cancellation-aware async design, hot or cold stream choices, and shared-state decisions directly caused by coroutine usage.

Do not use this skill as the primary source for general Kotlin language modeling, Kotlin test structure, framework-specific reactive APIs, Android architecture guidance, or JVM runtime internals.
