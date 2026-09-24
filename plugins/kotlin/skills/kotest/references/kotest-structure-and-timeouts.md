---
description: >-
  Open this when Kotest test grouping, lifecycle hooks, or timeouts are the blocker.
---

# Kotest Structure and Timeouts

Use the project's existing Kotest style.
Group tests only when a shared context makes the behavior clearer.

## Group related cases

```kotlin
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class BillingPolicyTest : DescribeSpec({
    describe("an overdue invoice") {
        it("applies a late fee") {
            service.lateFee() shouldBe Money("5.00")
        }
    }
})
```

Use `assertSoftly(subject)` for independent checks on one value, or `assertSoftly { }` for independent targets.
Do not use a soft block when later checks depend on an earlier assertion succeeding.

## Bound test execution

Kotest `.config(timeout = ...)` accepts a Kotlin `Duration`.
Coroutine timeouts are cooperative.
Use `blockingTest = true` only when blocking work must be interrupted.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds

class FeedRefreshTest : FunSpec({
    test("refresh finishes within budget").config(timeout = 500.milliseconds) {
        service.refresh() shouldBe RefreshResult.Done
    }
})
```

## Manage fixtures

Keep setup and cleanup at the spec level.
Use `beforeTest` and `afterTest` only when the tests share that lifecycle.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RepositoryTest : FunSpec({
    lateinit var repository: Repository
    beforeTest {
        repository = InMemoryRepository()
    }

    test("saves an entity") {
        repository.save(Entity("x"))
        repository.load("x") shouldBe Entity("x")
    }
})
```
