---
description: >-
  Open this when an external system reaches a state eventually and virtual time cannot prove the contract.
---

# Eventual Consistency

Use Kotest `eventually` when a real process or external service settles asynchronously.
Prefer deterministic scheduler control for delays owned by the test.
Use bounded Flow collection when emissions, rather than external polling, define the contract.

```kotlin
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class SettlementProjectionTest : FunSpec({
    test("projects the settlement eventually") {
        service.startProjection()
        eventually(5.seconds) {
            repository.status() shouldBe ProjectionStatus.READY
        }
    }
})
```

The duration bounds the total wait.
Assert the visible state inside the polling block, not the number of retries.
