---
title: Cancellation, Timeouts, and Failures
description: >-
  Open this when cancellation propagation, timeout behavior, cleanup, or coroutine failure semantics are the actual blocker.
---

Open this when the hard part is cancellation, failure propagation, or timeout-driven control flow.

## Rules

- let `CancellationException` propagate
- use `finally` for cleanup that must run during cancellation
- treat timeouts as cancellation of the suspended work
- remember that structured child failure normally cancels the parent scope
- use supervision only when one child failure should not cancel unrelated siblings

## Patterns

Cancellation-safe cleanup:

```kotlin
suspend fun useResource(): String {
    val resource = acquireResource()
    try {
        return process(resource)
    } finally {
        resource.close()
    }
}
```

Timeout around one bounded operation:

```kotlin
import kotlinx.coroutines.withTimeout

suspend fun loadSnapshot(): Snapshot = withTimeout(1_000) {
    repository.loadSnapshot()
}
```

Cooperative work in a long-running loop:

```kotlin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

suspend fun processAll(inputs: List<Input>) {
    for (input in inputs) {
        currentCoroutineContext().ensureActive()
        process(input)
    }
}
```

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| catching `Exception` and hiding cancellation | cancelled work continues unexpectedly | rethrow cancellation and handle only real failures |
| using timeout as normal branching without naming it | failure semantics become unclear | keep timeout boundaries explicit in the API |
| assuming `launch` failures behave like returned values | the parent may be cancelled instead | choose `async` only when the caller will await a value |
