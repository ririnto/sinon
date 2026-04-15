---
title: Null Safety and Collections
description: >-
  Reference for readable Kotlin null handling and collection-pipeline decisions without overusing scope functions.
---

Use this reference when the job is to rewrite one null-heavy, collection-heavy, or delegate-heavy Kotlin path into something readable. This reference should be sufficient on its own to finish that cleanup.

Use this file to finish one of these jobs:

- simplify a nullable flow without falling back to `!!`
- turn an overgrown collection chain into a readable pipeline
- decide whether `Sequence` materially helps a real transformation path
- keep `lazy`, `observable`, or `vetoable` usage explicit instead of surprising

Null-safety rules:

- prefer nullable types plus explicit handling over `!!`
- use early returns when absence is exceptional to the current path
- use scope functions only when they make ownership or transformation clearer

Collection guidance:

- use `map`, `filter`, and `associate` when the pipeline is still easy to read in one pass
- break long chains into named locals when business meaning is getting hidden
- use sequence-based processing only when laziness materially helps, not by default

Sequence example:

```kotlin
fun loadEnabledUsers(lines: List<String>): List<UserId> {
    val cleaned = lines
        .asSequence()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { raw -> raw.toLongOrNull() }
        .map(::UserId)
        .take(500)

    return cleaned.toList()
}
```

Use the eager collection form first. Switch to `asSequence()` only when the chain is selective enough or the source is large enough that avoiding intermediate collections materially helps.

Delegates:

- use `lazy` only when deferred initialization is part of the design and the thread-safety choice is intentional
- use `Delegates.observable` when state-change hooks are part of the model, not as a hidden side-effect channel
- use `Delegates.vetoable` when invalid writes should be rejected before the property changes

Examples:

```kotlin
import kotlin.LazyThreadSafetyMode

class ClientConfig {
    val endpoint: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        System.getenv("APP_ENDPOINT") ?: "http://localhost:8080"
    }
}
```

```kotlin
import kotlin.properties.Delegates

class RetryPolicy {
    var maxRetries: Int by Delegates.vetoable(3) { _, _, newValue ->
        newValue >= 0
    }
}
```

Prefer a named local over nested scope functions when the chain stops reading like direct business logic.
