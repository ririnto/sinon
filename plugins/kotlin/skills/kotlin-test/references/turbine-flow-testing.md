---
title: Turbine Flow Testing
description: >-
  Open this when step-by-step Flow inspection, cancellation verification, or error-terminal assertions are the blocker.
---

Open this when `take(n).toList()` is too coarse and you need item-by-item Flow inspection, explicit completion/cancellation verification, or error-state assertions.

## Dependency

```kotlin
dependencies {
    testImplementation("app.cash.turbine:turbine:1.2.1")
}
```

Pin Turbine explicitly unless your project already centralizes versions in a version catalog. The current documented Flow API uses `test { }` for one Flow and `testIn(backgroundScope)` inside `turbineScope { }` for multiple concurrent Flow probes.

## Basic item inspection

```kotlin
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UiStateRepositoryTest {
    @Test
    fun emitsLoadingThenDataThenIdle() = runTest {
        repository.observe().test {
            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Data(userId = "u-1"), awaitItem())
            assertEquals(UiState.Idle, awaitItem())
            awaitComplete()
        }
    }
}
```

## Expect no events

```kotlin
@Test
fun emitsNothingWhenIdle() = runTest {
    repository.observe().test {
        expectNoEvents()
        cancelAndIgnoreRemainingEvents()
    }
}
```

## Cancellation and cleanup

```kotlin
@Test
fun cancelsSubscriptionOnStop() = runTest {
    repository.observe().test {
        assertEquals(UiState.Loading, awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}
```

## Error terminal state

```kotlin
import app.cash.turbine.test
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DataRepositoryTest {
    @Test
    fun propagatesNetworkError() = runTest {
        val failingFlow: Flow<Data> = flow { throw IOException("connection refused") }

        failingFlow.test {
            awaitError()
        }
    }
}
```

## Multiple flows

```kotlin
import app.cash.turbine.testIn
import app.cash.turbine.turbineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardRepositoryTest {
    @Test
    fun observesStateAndEventsTogether() = runTest {
        turbineScope {
            val states = repository.observeState().testIn(backgroundScope)
            val events = repository.observeEvents().testIn(backgroundScope)

            repository.refresh()

            assertEquals(UiState.Loading, states.awaitItem())
            assertEquals(UiEvent.RefreshStarted, events.awaitItem())
            states.cancelAndIgnoreRemainingEvents()
            events.cancelAndIgnoreRemainingEvents()
        }
    }
}
```

## Rules

- always consume the turbine fully (`awaitComplete()`, `awaitError()`, `cancelAndIgnoreRemainingEvents()`, or `cancel()`)
- prefer `test { }` over `take(n).toList()` when emission order or terminal state is part of the contract
- prefer `testIn(backgroundScope)` inside `turbineScope { }` when coordinating multiple flows in one test
- use `expectNoEvents()` to assert silence rather than assuming no emission means success

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| leaving turbine open without consuming terminal | leak warning or hanging test | always call awaitComplete/awaitError/cancel variant |
| using Turbine for simple single-value flows | adds indirection over `first()` or `single()` | use `first()` when only final value matters |
