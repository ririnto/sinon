---
title: Coroutine Scope Patterns
description: >-
  Reference for coroutine scope ownership, lifecycle boundaries, dispatcher usage, and cancellation-safe cleanup patterns.
---

Use this reference when the hard part is scope ownership, lifecycle, or cancellation semantics.

Scope rules:

- prefer `coroutineScope` when child work must succeed or fail together
- prefer `supervisorScope` when sibling failures should not automatically cancel unrelated work
- keep scope creation at lifecycle boundaries rather than scattering it through leaf functions

Dispatcher guidance:

- keep dispatcher switching explicit at blocking or CPU-bound boundaries
- do not hide blocking calls inside apparently lightweight suspend functions

Cancellation guidance:

- let cancellation propagate naturally
- do not swallow `CancellationException` in broad exception handlers
- make cleanup explicit in `finally` blocks when resources must be released

## Concrete Scope Examples

Parallel work with shared ownership (coroutineScope — one child failure cancels all siblings):

```kotlin
suspend fun loadDashboard(): Dashboard = coroutineScope {
    val summary = async { summaryService.load() }
    val alerts = async { alertService.load() }

    Dashboard(summary.await(), alerts.await())
}
```

Sibling isolation (supervisorScope — one child failure does not cancel siblings):

```kotlin
suspend fun loadWidgets(): List<WidgetResult> = supervisorScope {
    listOf(
        async { loadOne("a") },
        async { loadOne("b") },
    ).map { runCatching { it.await() }.getOrElse { WidgetResult.Failed } }
}
```

Scope at lifecycle boundary — structured ownership that ties child work to the surrounding job:

```kotlin
class OrderPresenter(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) {
    fun loadOrders() {
        scope.launch {
            val orders = repository.observeOrders().first()
            view.render(orders)
        }
    }

    // Or inherit scope from a ViewModel:
    private fun loadOrders() {
        viewModelScope.launch {
            val orders = repository.observeOrders().first()
            view.render(orders)
        }
    }
}
```

Explicit dispatcher at real blocking boundary:

```kotlin
suspend fun importCsv(path: Path): ImportResult = withContext(Dispatchers.IO) {
    // blocking I/O stays on IO dispatcher, does not pin Main or Default
    Files.readAllLines(path).map { parseRow(it) }
}
```

Cancellation-safe cleanup in finally:

```kotlin
suspend fun useResource(): String = coroutineScope {
    val resource = acquireResource()
    try {
        resource.use { process(it) }
    } finally {
        // Cancellation does not leak; cleanup runs before scope fails
        resource.close()
    }
}
```

Detached launch without scope — avoids structured cancellation:

```kotlin
// Anti-pattern: launching without a clear owner
launch {
    doWork()  // detached; cancellation orphaned
}

// Preferred: attach to an explicit scope
scope.launch {
    doWork()  // inherits scope lifecycle; cancels with scope
}
