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

## Additional Patterns

Cleanup that must not be cancelled:

```kotlin
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive

suspend fun processWithMandatoryCleanup(inputs: List<Input>) {
    val resource = acquireResource()
    try {
        for (input in inputs) {
            ensureActive()
            process(resource, input)
        }
    } finally {
        withContext(NonCancellable) {
            resource.close()
        }
    }
}
```

Non-throwing timeout variant:

```kotlin
import kotlinx.coroutines.withTimeoutOrNull

suspend fun loadWithFallback(orderId: OrderId): Order? =
    withTimeoutOrNull(1_000) {
        repository.load(orderId)
    } ?: fallbackCache.get(orderId)
```

Cooperative yielding in CPU-heavy loops:

```kotlin
import kotlinx.coroutines.yield

suspend fun heavyComputation(data: List<Data>): Result {
    var accumulator = Initial
    for ((index, item) in data.withIndex()) {
        accumulator = combine(accumulator, transform(item))
        if (index % 1000 == 0) yield()
    }
    return accumulator
}
```

Decision: Use `ensureActive()` when you only need to check cancellation without yielding. Use `yield()` when you want to both check cancellation AND allow other coroutines to run.

Selective cancellation -- use `cancelChildren()` to cancel all child coroutines without cancelling the parent scope itself, and `Job.join()` to wait for a specific child coroutine to finish:

```kotlin
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren

suspend fun gracefulShutdown(scope: CoroutineScope) {
    scope.cancelChildren()
    cleanupJob.join()
}
```

Bridging a callback-based API at the suspend function level (distinct from `callbackFlow` which bridges at the Flow level):

```kotlin
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCancellableCoroutine

suspend fun fetchUserData(userId: String): UserData =
    suspendCancellableCoroutine { continuation ->
        val callback = object : ApiCallback {
            override fun onSuccess(data: UserData) {
                continuation.resume(data)
            }
            override fun onError(error: Throwable) {
                continuation.resumeWithException(error)
            }
        }
        apiClient.fetchUser(userId, callback)
        continuation.invokeOnCancellation { apiClient.cancel(userId) }
    }
```

Use `suspendCancellableCoroutine` when the callback contract is a single async result (one-shot). Use `callbackFlow` (covered in the Flow selection reference) when the callback emits multiple values over time. The `invokeOnCancellation` block handles resource cleanup when the caller cancels before the callback completes.
