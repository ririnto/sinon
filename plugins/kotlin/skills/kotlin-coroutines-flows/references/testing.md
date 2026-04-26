---
title: Testing Coroutines and Flows
description: >-
  Open this when writing tests for coroutine or Flow code, controlling virtual time,
  verifying emissions, or testing cancellation behavior.
---

Open this when the task is testing coroutine or Flow code.

This reference is a minimal bridge for coroutine-design work. For full Kotlin test structure, dependency setup, Turbine usage, and JUnit/Kotest choices, use the `kotlin-test` skill's ordinary path.

## Rules

- use `runTest` from `kotlinx.coroutines.test` as the standard test entry point
- prefer `StandardTestDispatcher` for time-controlled tests
- use `UnconfinedTestDispatcher` only for synchronous execution without time control
- start the delayed work, advance virtual time, then call `runCurrent()` or `advanceUntilIdle()` to run tasks scheduled at the new virtual time
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

Time-controlled test -- start the delayed work, advance time, then run tasks scheduled at the target time:

```kotlin
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Test
fun timeoutEmitsFallback() = runTest {
    val result = async { slowRepository.loadWithTimeout(OrderId("1")) }

    advanceTimeBy(5_000)
    runCurrent()

    assertEquals(Fallback, result.await())
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

@Test
fun cancellationRunsCleanup() {
    val resource = TrackingResource()
    val scope = TestScope()
    val job: Job = scope.launch {
        resource.use { r -> r.longOperation() }
    }

    job.cancel()
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
