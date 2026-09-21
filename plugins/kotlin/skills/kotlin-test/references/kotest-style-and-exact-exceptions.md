---
description: >-
  Open this when Kotest style, soft assertions, or exact exception checks are the blocker.
---

# Kotest Style and Exact Exceptions

Open this when the project already uses Kotest and the remaining blocker is keeping assertion style consistent.

## Rules

- keep Kotest examples inside the suite's existing style.
  Do not mix styles.
- use `assertSoftly` when several assertions describe one observable behavior
- in an unambiguous receiver lambda, call `shouldBe(expected)` rather than writing `this shouldBe expected`
  - keep an explicit receiver when multiple receivers make omission ambiguous
- use `shouldThrowExactly<T>()` when the exact exception type matters
- assert the caught exception's `message` with `shouldBe` against the exact expected text, and check the meaningful fields it declares
- use `shouldNotThrowAny` when the no-exception property itself is the contract
- do not place `shouldThrowExactly`, `shouldThrowAny`, or other throwing assertions inside `assertSoftly`.
  They abort the soft block immediately, so later assertions never run.
- place lifecycle hooks at the spec level

## Spec styles

Choose the style the project already uses.

```kotlin
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class ProfileServiceTest : FunSpec({
    test("returns cached profile") {
        service.loadProfile("user-1") shouldBe Profile("user-1")
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
            then("size becomes 1") {
                cart shouldHaveSize 1
            }
        }
    }
})
```

## Common matchers

```kotlin
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

result shouldBe expected
result shouldNotBe unexpected
list shouldContain item
list shouldHaveSize 3
value shouldBeInstanceOf<String>()
nullable.shouldBeNull()
message shouldContain "error"
shouldThrowExactly<RetryException> {
    service.run()
}.message shouldBe "retry budget exhausted"
val parsed = shouldNotThrowAny {
    parser.parse(raw)
}
```

## Soft assertions

```kotlin
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class ProfileServiceKotestTest : FunSpec({
    test("returns cached profile with correct fields") {
        assertSoftly(service.loadProfile("user-1")) { profile ->
            shouldBe(Profile("user-1"))
            id shouldBe "user-1"
            isActive.shouldBeTrue()
        }
    }
})
```

## Exact exception check

`shouldThrowExactly<T>` fails when the block throws a subclass of `T` instead of `T` itself, so the test proves the precise exception contract.
It returns the caught exception, so the message and fields stay available for exact checks.
Import it from `io.kotest.assertions.throwables.shouldThrowExactly`.

```kotlin
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RetryBudgetException(message: String, val attemptedBudget: Int) :
    RuntimeException(message)

class RetryPolicyKotestTest : FunSpec({
    test("rejects invalid retry budget") {
        val error = shouldThrowExactly<RetryBudgetException> {
            service.configure(RetryPolicy(budget = -1))
        }
        error.message shouldBe "retry budget must be positive, got -1"
        error.attemptedBudget shouldBe -1
    }
})
```

The `RetryBudgetException` declaration above shows the assumed application-owned exception type.
Assert only the fields the exception actually declares, and copy the real message format from its construction site so the expectation stays deterministic across library upgrades.

## No-exception contract

`shouldNotThrowAny` proves that a block completes without throwing.
Use it only when the no-exception property itself is the observable contract, such as a parsing edge case that must succeed or a migration that must tolerate legacy input.
Do not wrap ordinary happy-path code in it just to mirror the implementation, and do not wrap assertions from other libraries in it merely as a formality.

```kotlin
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LegacyConfigKotestTest : FunSpec({
    test("parses a config with an empty optional section") {
        shouldNotThrowAny {
            AppConfigParser.parse("""
                [general]
                name = app
                [optional-section-may-be-empty]
            """.trimIndent())
        }.name shouldBe "app"
    }
})
```

## Lifecycle listeners

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

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
