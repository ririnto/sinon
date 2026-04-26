---
title: Kotest Style and Exact Exceptions
description: >-
  Open this when Kotest style, soft assertions, or exact exception checks are the blocker.
---

Open this when the project already uses Kotest and the remaining blocker is keeping assertion style consistent.

## Rules

- keep Kotest examples inside the suite's existing style; do not mix styles
- use `assertSoftly` when several assertions describe one observable behavior
- use `shouldThrowExactly<T>()` when the exact exception type matters
- place lifecycle hooks at the spec level

## Spec styles

Choose the style the project already uses.

```kotlin
class ProfileServiceTest : FunSpec({
    test("returns cached profile") {
        val result = service.loadProfile("user-1")
        result shouldBe Profile("user-1")
    }
})

class OrderServiceTest : DescribeSpec({
    describe("checkout") {
        it("calculates total") { }
        it("applies discount") { }
    }
})

class CartTest : BehaviorSpec({
    given("an empty cart") {
        `when`("an item is added") {
            then("size becomes 1") { cart.size shouldBe 1 }
        }
    }
})
```

## Common matchers

```kotlin
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.assertions.assertSoftly

result shouldBe expected
result shouldNotBe unexpected
list shouldContain item
list shouldHaveSize 3
value shouldBeInstanceOf<String>()
nullable.shouldBeNull()
message shouldContain "error"
```

## Soft assertions

```kotlin
class ProfileServiceKotestTest : FunSpec({
    test("returns cached profile with correct fields") {
        val result = service.loadProfile("user-1")
        assertSoftly(result) {
            it shouldBe Profile("user-1")
            it.id shouldBe "user-1"
            it.isActive shouldBe true
        }
    }
})
```

## Exact exception check

```kotlin
class RetryPolicyKotestTest : FunSpec({
    test("rejects invalid retry budget") {
        val error = shouldThrowExactly<RetryException> {
            service.run()
        }
        error.message shouldBe "retry budget exhausted"
    }
})
```

## Lifecycle listeners

```kotlin
class DatabaseRepositoryTest : FunSpec({
    lateinit var repo: Repository

    beforeTest {
        repo = InMemoryRepository()
    }

    test("saves and loads") {
        repo.save(Entity("x"))
        repo.load("x") shouldBe Entity("x")
    }
})
```
