---
title: Kotest Style and Exact Exceptions
description: >-
  Open this when Kotest style, soft assertions, or exact exception checks are the blocker.
---

Open this when the project already uses Kotest and the remaining blocker is keeping the assertion style consistent.

## Rules

- keep Kotest examples inside the suite's existing style rather than mixing styles arbitrarily
- use `assertSoftly` when several assertions describe one observable behavior
- use `shouldThrowExactly<T>()` when the exact exception type matters

## Patterns

Soft assertions for one behavior:

```kotlin
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ProfileServiceKotestTest : FunSpec() {
    init {
        test("returns cached profile") {
            val result = service.loadProfile("user-1")
            assertSoftly(result) {
                it shouldBe Profile("user-1")
                it.id shouldBe "user-1"
            }
        }
    }
}
```

Exact exception check:

```kotlin
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import kotlin.test.assertEquals

class RetryPolicyKotestTest : FunSpec() {
    init {
        test("rejects invalid retry budget") {
            val error = shouldThrowExactly<RetryException> {
                service.run()
            }
            assertEquals("retry budget exhausted", error.message)
        }
    }
}
```
