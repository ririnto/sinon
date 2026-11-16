# Kotest Core Decisions

## Start with Kotest

Use `FunSpec` and Kotest matchers for new JVM test suites.
Use `assertSoftly` when several assertions describe one observable behavior.
The snippet is an adapted spec fragment.
Replace its application types and service with the target's fixtures.

```kotlin
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ProfileServiceTest : FunSpec({
    test("returns cached profile") {
        assertSoftly(service.loadProfile(PROFILE_ID)) {
            shouldBe(Profile(PROFILE_ID))
            id shouldBe PROFILE_ID
            isActive.shouldBeTrue()
            error.shouldBeNull()
        }
    }

    test("returns tags in contract order") {
        service.loadTags(PROFILE_ID) shouldContainExactly listOf("admin", "editor")
    }
}) {
    companion object {
        private const val PROFILE_ID: String = "user-1"
    }
}
```

Use these common matchers:

| Matcher | When to use it |
| --- | --- |
| `actual shouldBe expected` | value equality is the contract |
| `actual shouldNotBe unexpected` | a specific value must not match |
| `actual.shouldBeTrue()` / `actual.shouldBeFalse()` | a boolean predicate is the contract |
| `actual.shouldBeNull()` / `actual.shouldNotBeNull()` | nullability is part of the contract |
| `actual shouldContainExactly expected` | ordered collection contents are the contract |
| `actual shouldContain item` | membership is the contract |
| `actual shouldBeInstanceOf<Type>()` | runtime type is the contract |
| `shouldThrowExactly<Type> { ... }` | exact exception type and its fields are the contract |

Use Kotest styles to group related test cases, and use descriptive test names instead of annotation-based display labels.
Use Kotest lifecycle hooks for fixture setup and cleanup.

## Use suspending tests directly

Kotest `FunSpec` test bodies are suspending.
Enable `coroutineTestScope` for virtual-time control.
Kotest runs those tests with a test dispatcher.

When `withTimeout` and `delay` must run against virtual time, enable `coroutineTestScope` for that test.
Start the timed operation first, advance virtual time, then flush the scheduler before awaiting the result:

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async

class OrderServiceTest : FunSpec({
    test("returns fallback on timeout").config(coroutineTestScope = true) {
        val result = async { service.loadWithTimeout(OrderId("1")) }
        testCoroutineScheduler.advanceTimeBy(5_000)
        testCoroutineScheduler.runCurrent()
        result.await() shouldBe Fallback
    }
})
```

Assert the observable timeout behavior without catching cancellation to make a test pass.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OrderSummaryServiceTest : FunSpec({
    test("loads order summary") {
        service.loadSummary(OrderId("o-1")) shouldBe OrderSummary("o-1")
    }
})
```

## Keep Flow collection bounded

Use `first()`, `single()`, or `take(n).toList()` to prove a finite contract and finish the test.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList

class UiStateRepositoryTest : FunSpec({
    test("emits loading then data") {
        repository.observe().take(2).toList() shouldContainExactly listOf(UiState.Loading, UiState.Data)
    }
})
```

## Use direct exception assertions

Use `shouldThrowExactly<T>()` when the exact exception type is part of the contract.
Use `shouldThrow<T>()` when a subtype also satisfies it.
Both return the exception, so message and property checks stay explicit.

```kotlin
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RetryPolicyTest : FunSpec({
    test("rejects invalid retry budget") {
        shouldThrowExactly<IllegalArgumentException> {
            service.configure(-1)
        }.message shouldBe "retry budget must be non-negative"
    }
})
```

Assert the exact message and meaningful exception fields when they are part of the contract.
Use `shouldNotThrowAny { }` only when no-exception is itself the contract.
Open the Kotest reference for the exact shapes and the `assertSoftly` interaction caveat.
