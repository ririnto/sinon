---
description: >-
  Open this when selecting a Kotest spec style, nesting tests, or choosing isolation and lifecycle behavior.
---

# Kotest Spec Styles

Kotest 6.2 documents nine styles with equivalent test behavior.
Their definition syntax differs.
Keep the style already used in a suite.
Choose `FunSpec` for new suites when no existing convention applies.
Every leaf test body is suspending, so call suspend functions directly without a wrapper.

## Flat tests

`FunSpec` uses `test` and groups related cases under `context` when they share a meaningful condition.

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class OrderFunSpec : FunSpec({
    context("order lookup") {
        test("finds a stored order") {
            service.find(OrderId("1")) shouldBe Order("1")
        }

        test("returns null for a missing order") {
            service.find(OrderId("missing")).shouldBeNull()
        }
    }
})
```

`StringSpec` uses the test name as a string receiver.
It has root tests but no nested containers.
Use `FreeSpec` when nesting is needed.

```kotlin
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class OrderStringSpec : StringSpec({
    "loads an order" {
        service.load(OrderId("1")) shouldBe Order("1")
    }
})
```

## Arbitrarily nested tests

`FreeSpec` puts `-` on a container and omits it from each leaf test.

```kotlin
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class OrderFreeSpec : FreeSpec({
    "checkout" - {
        "valid order" - {
            "loads an order" {
                service.load(OrderId("1")) shouldBe Order("1")
            }
        }
    }
})
```

`WordSpec` uses `should` for the context and string leaf tests.

```kotlin
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class OrderWordSpec : WordSpec({
    "checkout" should {
        "load an order" {
            service.load(OrderId("1")) shouldBe Order("1")
        }
    }
})
```

`ShouldSpec` nests `should` under `context` when grouping is useful.
`ExpectSpec` uses the same grouping with `expect` leaf tests.

```kotlin
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class OrderShouldSpec : ShouldSpec({
    context("checkout") {
        should("load an order") {
            service.load(OrderId("1")) shouldBe Order("1")
        }
    }
})
```

```kotlin
import io.kotest.core.spec.style.ExpectSpec
import io.kotest.matchers.shouldBe

class OrderExpectSpec : ExpectSpec({
    context("checkout") {
        expect("an order") {
            service.load(OrderId("1")) shouldBe Order("1")
        }
    }
})
```

## Scenario-oriented tests

`DescribeSpec` uses `describe` containers and `it` leaf tests.
`FeatureSpec` uses `feature` containers and `scenario` leaf tests.
`BehaviorSpec` uses `given`, backtick-escaped `` `when` ``, and `then`.

```kotlin
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class OrderDescribeSpec : DescribeSpec({
    describe("checkout") {
        it("loads an order") {
            service.load(OrderId("1")) shouldBe Order("1")
        }
    }
})
```

```kotlin
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe

class OrderFeatureSpec : FeatureSpec({
    feature("checkout") {
        scenario("load an order") {
            service.load(OrderId("1")) shouldBe Order("1")
        }
    }
})
```

```kotlin
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class OrderBehaviorSpec : BehaviorSpec({
    given("checkout") {
        `when`("an order exists") {
            then("load it") {
                service.load(OrderId("1")) shouldBe Order("1")
            }
        }
    }
})
```

## Lifecycle and isolation

Use `beforeTest` or `afterTest` at the spec level for all tests, including containers.
Use `beforeEach` or `afterEach` for leaf tests.
Use `beforeContainer` and `afterContainer` for containers.
`SingleInstance` is the default isolation mode: one spec instance runs its tests, so mutable properties can leak between tests.
`InstancePerRoot` creates one instance per root test.
Do not use the deprecated `InstancePerTest` and `InstancePerLeaf` modes for new specs.
Nested edge cases have undefined behavior.
Set an isolation mode when fixture ownership requires it.
Prefer per-test local values for simple tests.

```kotlin
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IsolatedOrderSpec : FunSpec({
    beforeEach {
        repository.clear()
    }

    test("loads an order") {
        service.load(OrderId("1")) shouldBe Order("1")
    }
}) {
    override fun isolationMode(): IsolationMode = IsolationMode.InstancePerRoot
}
```
