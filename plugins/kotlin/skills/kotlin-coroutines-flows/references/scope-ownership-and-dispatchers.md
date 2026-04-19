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

Keep blocking work on the right dispatcher:

```kotlin
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun importCsv(path: Path): List<Row> = withContext(Dispatchers.IO) {
    Files.readAllLines(path).map(::parseRow)
}
```

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| creating ad hoc scopes in leaf functions | ownership leaks out of the call graph | inject or inherit the owning scope |
| using `supervisorScope` as the default | failures stop being visible too early | keep normal structured failure unless isolation is required |
| scattering `withContext` around lightweight code | thread intent becomes noisy and misleading | switch context only at real boundaries |
