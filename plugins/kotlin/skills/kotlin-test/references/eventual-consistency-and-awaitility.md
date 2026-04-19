---
title: Eventual Consistency and Awaitility
description: >-
  Open this when eventual consistency requires Awaitility rather than scheduler control or bounded collection.
---

Open this when deterministic scheduler control cannot prove the contract and the system is genuinely eventual.

## Rules

- prefer scheduler control or bounded Flow collection first
- use Awaitility only when eventual consistency is the real contract
- keep the wait bounded and assert the visible outcome, not internal timing

## Pattern

```kotlin
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import org.awaitility.kotlin.await

class SettlementProjectionTest {
    @Test
    fun projectsTheSettlementEventually() {
        service.startProjection()

        await.atMost(Duration.ofSeconds(5)).untilAsserted {
            assertEquals(ProjectionStatus.READY, repository.status())
        }
    }
}
```

## Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| reaching for Awaitility when `runTest` or bounded collection could prove the same thing | the test becomes slower and less deterministic | use Awaitility only for truly eventual systems |
| asserting polling internals instead of the visible result | the test misses the real contract | assert the externally observable state |
