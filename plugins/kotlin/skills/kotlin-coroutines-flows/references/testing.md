---
title: Testing Coroutines and Flows
description: >-
  Open this when writing tests for coroutine or Flow code, controlling virtual time,
  verifying emissions, or testing cancellation behavior.
---

Open this when the task is testing coroutine or Flow code.

## Rules

- use `runTest` from `kotlinx.coroutines.test` as the standard test entry point
- prefer `StandardTestDispatcher` for time-controlled tests
- use `UnconfinedTestDispatcher` only for synchronous execution without time control
- advance time with `advanceTimeBy()` and flush pending work with `advanceUntilIdle()`
- verify Flow emissions with bounded collection (`first()`, `take(n).toList()`, or Turbine)
- test cancellation by launching in a `TestScope`, cancelling, and verifying cleanup

## Patterns

Basic suspend function test:

```kotlin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Test
fun loadOrderReturnsData() = runTest {
    val loader = OrderLoader(FakeOrderRepository())
    val order = loader.load(OrderId("123"))
    assertEquals("123", order.id.value)
}
```

Time-controlled test -- advance time BEFORE the operation that depends on timing:

```kotlin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Test
fun timeoutEmitsFallback() = runTest {
    advanceTimeBy(5_000)
    val result = slowRepository.loadWithTimeout(OrderId("1"))
    assertEquals(Fallback, result)
}
```

Testing a StateFlow:

```kotlin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Test
fun stateFlowEmitsInitialThenUpdated() = runTest {
    val viewModel = OrderViewModel(repository)
    assertEquals(UiState.Loading, viewModel.uiState.first())
    viewModel.load(OrderId("1"))
    assertEquals(UiState.Data(order), viewModel.uiState.first())
}
```

Testing cancellation triggers cleanup:

```kotlin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

@Test
fun cancellationRunsCleanup() {
    val resource = TrackingResource()
    val scope = TestScope()
    scope.launch {
        resource.use { r -> r.longOperation() }
    }
    scope.advanceUntilIdle()
    assertTrue(resource.wasClosed)
}
```

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| using `runBlocking` in tests | does not support time control or test dispatchers | use `runTest` |
| assuming `launch` inside `runTest` executes synchronously | StandardTestDispatcher schedules, does not run immediately | use `advanceUntilIdle()` or `UnconfinedTestDispatcher` |
| not advancing time for delayed assertions | timeouts and delays never resolve | call `advanceTimeBy()` or `advanceUntilIdle()` |
| testing Flow with `collect` directly in test body | may hang if the flow is infinite or cold | use `first()`, `take(n).toList()`, or Turbine |
