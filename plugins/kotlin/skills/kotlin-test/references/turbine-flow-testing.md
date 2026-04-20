---
title: Turbine Flow Testing
description: >-
  Open this when step-by-step Flow inspection, cancellation verification, or error-terminal assertions are the blocker.
---

Open this when `take(n).toList()` is too coarse and you need item-by-item Flow inspection, explicit completion/cancellation verification, or error-state assertions.

## Dependency

```kotlin
testImplementation("app.cash.turbine:turbine")
```

## Basic item inspection

```kotlin
import app.cash.turbine.turbine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UiStateRepositoryTest {
    @Test
    fun emitsLoadingThenDataThenIdle() = runTest {
        repository.observe().turbine {
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
    repository.observe().turbine {
        expectNoEvents()
    }
}
```

## Cancellation and cleanup

```kotlin
@Test
fun cancelsSubscriptionOnStop() = runTest {
    repository.observe().turbine {
        assertEquals(UiState.Loading, awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}
```

## Error terminal state

```kotlin
@Test
fun propagatesNetworkError() = runTest {
    val failingFlow: Flow<Data> = flow { throw IOException("connection refused") }

    failingFlow.turbine {
        awaitError()
    }
}
```

## Rules

- always consume the turbine fully (`awaitComplete()`, `awaitError()`, `cancelAndIgnoreRemainingEvents()`, or `cancel()`)
- prefer `turbine { }` over `take(n).toList()` when emission order or terminal state is part of the contract
- use `expectNoEvents()` to assert silence rather than assuming no emission means success

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| leaving turbine open without consuming terminal | leak warning or hanging test | always call awaitComplete/awaitError/cancel variant |
| using Turbine for simple single-value flows | adds indirection over `first()` or `single()` | use `first()` when only final value matters |
