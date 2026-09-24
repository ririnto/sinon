---
description: >-
  Use this reference for Kotest style, soft assertions, and exact exception checks.
---

# Kotest Style and Exact Exceptions

Open this when the project already uses Kotest and the remaining blocker is keeping assertion style consistent.

## Rules

- keep Kotest examples inside the suite's existing style.
  Do not mix styles.
- use `assertSoftly` only when several independent assertions must continue after earlier failures
- use direct matchers for one assertion, a dependent assertion chain, or exception assertions
- in an unambiguous receiver lambda, call `shouldBe(expected)` rather than writing `this shouldBe expected`
  - keep an explicit receiver when multiple receivers make omission ambiguous
- use `shouldThrowExactly<T>()` for exception assertions to require the exact type
- capture the returned exception and assert its exact stable message or meaningful contract field
- use `shouldNotThrowAny` only when the no-exception property itself is the contract
- do not place `shouldThrowExactly` or another throwing assertion inside `assertSoftly`.
  They abort the soft block immediately, so later assertions never run.
- place lifecycle hooks at the spec level

## Exact matchers and null boundaries

Prefer `shouldBeTrue()`, `shouldBeFalse()`, `shouldBeNull()`, and `shouldNotBeNull()` for boolean and null contracts.
Use `shouldBeEmpty()` for an empty collection, `shouldHaveSize(n)` for a nonzero size contract, and `shouldContainExactly` for exact ordered contents.
Assert the non-null boundary before checking a nullable value's properties.

```kotlin
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class LookupTest : FunSpec({
    test("reports a found profile") {
        repository.lookup("user-1").shouldNotBeNull().id shouldBe "user-1"
    }

    test("has no pending updates") {
        assertSoftly(repository) {
            pendingUpdates().shouldBeEmpty()
            isUpdating().shouldBeFalse()
        }
    }
})
```

## Soft assertions

```kotlin
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class ProfileServiceKotestTest : FunSpec({
    test("returns cached profile with correct fields") {
        assertSoftly(service.loadProfile("user-1")) {
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
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RetryPolicyKotestTest : FunSpec({
    test("rejects invalid retry budget") {
        assertSoftly(shouldThrowExactly<RetryBudgetException> {
            service.configure(RetryPolicy(budget = -1))
        }) {
            message shouldBe "retry budget must be positive, got -1"
            attemptedBudget shouldBe -1
        }
    }
}) {
    companion object {
        private class RetryBudgetException(message: String, val attemptedBudget: Int) : RuntimeException(message)
    }
}
```

The `RetryBudgetException` declaration above shows the assumed application-owned exception type.
Assert fields declared on the exception, and copy the message format from its construction site.

## No-exception contract

`shouldNotThrowAny` proves that a block completes without throwing.
Use it only when the no-exception property itself is the observable contract, such as a parsing edge case that must succeed or a migration that must tolerate legacy input.
Reserve `shouldNotThrowAny` for a no-exception contract.
Assert values or outcomes in ordinary happy-path tests.

```kotlin
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec

class LegacyConfigKotestTest : FunSpec({
    test("parses a config with an empty optional section") {
        shouldNotThrowAny {
            AppConfigParser.parse("""
                [general]
                name = app
                [optional-section-may-be-empty]
            """.trimIndent())
        }
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
