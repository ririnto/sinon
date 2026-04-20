---
title: Scope Ownership and Dispatchers
description: >-
  Open this when coroutine ownership, supervision, launch boundaries, or dispatcher placement is the actual blocker.
---

Open this when you need to decide who owns coroutine work, where to launch it, or where dispatcher changes belong.

## Scope Rules

- keep child work inside the current suspend function when the work should succeed or fail as one unit
- use an explicit `CoroutineScope` only when a lifecycle or longer-lived owner is part of the contract
- prefer `coroutineScope` when child failure should cancel sibling work
- prefer `supervisorScope` only when sibling isolation is an intentional requirement
- keep dispatcher changes at real blocking or CPU-heavy boundaries

## Dispatcher Catalog

| Dispatcher | Pool | Best for | Avoid |
| --- | --- | --- | --- |
| `Dispatchers.Default` | CPU-bound (core count) | Computation, sorting, parsing | Blocking I/O, UI work |
| `Dispatchers.IO` | Shared I/O pool (~64 threads) | File, network, database I/O | CPU-heavy computation |
| `Dispatchers.Main` | Main/UI thread | UI updates, quick dispatches | Blocking, CPU-heavy work |
| `Dispatchers.Unconfined` | Resumes on caller's thread | Tests, non-blocking bridges | Production concurrency |

Root scope with exception handler:

```kotlin
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.SupervisorJob

val applicationScope = CoroutineScope(
    SupervisorJob() +
    Dispatchers.Default +
    CoroutineExceptionHandler { _, throwable ->
        logger.error("Uncaught coroutine exception", throwable)
    }
)
```

Invariant: `CoroutineExceptionHandler` installed on a child scope does NOT catch exceptions from sibling coroutines. Install it only at the root scope or directly on `launch`/`async`.

## Patterns

All children succeed or fail together:

```kotlin
suspend fun loadDashboard(): Dashboard = coroutineScope {
    val summary = async { summaryService.load() }
    val alerts = async { alertService.load() }
    Dashboard(summary.await(), alerts.await())
}
```

Sibling isolation is intentional:

```kotlin
suspend fun loadWidgets(): List<WidgetResult> = supervisorScope {
    listOf(
        async { loadOne("a") },
        async { loadOne("b") }
    ).map { deferred ->
        runCatching { deferred.await() }.getOrElse { WidgetResult.Failed }
    }
}
```

Launch from a visible owner:

```kotlin
class OrderPresenter(private val scope: CoroutineScope) {
    fun loadOrders() {
        scope.launch {
            view.render(repository.observeOrders().first())
        }
    }
}
```

Control parallelism for CPU-bound work without creating extra threads. `limitedParallelism(n)` creates a dispatcher view that limits concurrent execution to `n` threads from the parent pool:

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private val boundedCompute = Dispatchers.Default.limitedParallelism(4)

suspend fun processAll(items: List<Input>): List<Output> = withContext(boundedCompute) {
    coroutineScope {
        items.map { item -> async { heavyTransform(item) } }.awaitAll()
    }
}
```

Use when you need to limit parallelism of CPU-bound work (e.g., to avoid exhausting CPU cores or rate-limiting external calls). The canonical `withContext(Dispatchers.IO)` and `withContext(Dispatchers.Default)` patterns are in `SKILL.md` under "Ownership and dispatchers".

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| creating ad hoc scopes in leaf functions | ownership leaks out of the call graph | inject or inherit the owning scope |
| using `supervisorScope` as the default | failures stop being visible too early | keep normal structured failure unless isolation is required |
| scattering `withContext` around lightweight code | thread intent becomes noisy and misleading | switch context only at real boundaries |
