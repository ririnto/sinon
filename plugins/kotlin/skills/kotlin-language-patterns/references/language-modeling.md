---
title: Language Modeling Guide
description: >-
  Reference for choosing Kotlin type shapes such as value classes, data classes, sealed hierarchies, and singleton objects.
---

Use this reference when the job is to choose the right Kotlin type shape for a model that is still unclear after reading `SKILL.md`. This reference should be sufficient on its own to finish that modeling decision.

Choose the narrowest construct that matches the domain meaning:

- `value class` for a single wrapped value whose domain meaning should be stronger than a raw primitive or `String`
- `data class` for immutable value carriers with meaningful structural equality
- regular `class` for stateful behavior or identity-bearing objects
- `object` for stateless singleton behavior
- `enum class` for a small fixed set of simple constants
- `sealed interface` or `sealed class` for a closed variant set with distinct payloads

Decision checklist:

1. Does the type wrap exactly one meaningful value such as `UserId`, `OrderId`, or `EmailAddress`? Prefer `value class`.
2. Is the type primarily a value with several immutable fields? Prefer `data class`.
3. Is the variant set intentionally closed inside the module? Prefer sealed modeling.
4. Will Java callers consume this type directly? Keep interoperability obvious and avoid surprising Kotlin-only tricks in the public API.
5. Does mutation carry domain meaning? Use a regular class and keep state transitions explicit.

Prefer public API shapes that read clearly from both Kotlin and Java when interop matters.

Use this file to finish one of these jobs:

- decide whether a single wrapped concept should become a `value class`
- choose between `data class`, regular `class`, and `object`
- decide whether a variant set is truly closed enough for sealed modeling
- make a Java-facing public model read clearly without Kotlin-only surprises

Smallest code shape that fits the model:

Single wrapped meaning with `value class`:

```kotlin
@JvmInline
value class UserId(val value: String)
```

Use when: one wrapped value needs stronger domain meaning than a raw `String` or primitive.

Small fixed constants with `enum class`:

```kotlin
enum class InvoiceStatus {
    DRAFT,
    SENT,
    PAID,
}
```

Use when: the set is small, constant-like, and each value does not need distinct payload data.

Immutable multi-field value with `data class`:

```kotlin
data class Customer(
    val id: UserId,
    val email: String,
)
```

Use when: the type is primarily a value carrier with immutable fields and structural equality is desirable.

Stateless singleton behavior with `object`:

```kotlin
object IsoCountryCodes {
    fun isSupported(code: String): Boolean = code in setOf("US", "JP", "DE")
}
```

Use when: one shared stateless behavior or registry should exist only once in the process.

Closed variant set with sealed modeling:

```kotlin
sealed interface PaymentResult {
    data class Approved(val authorizationId: String) : PaymentResult
    data class Rejected(val reason: String) : PaymentResult
}
```

Use when: the variant set is intentionally closed inside the module and each branch may carry different payload data.

Stateful or identity-bearing behavior with a regular class:

```kotlin
class RetryBudget(
    private var remaining: Int,
) {
    fun consume(): Boolean {
        if (remaining == 0) {
            return false
        }
        remaining -= 1
        return true
    }
}
```

Use when: mutation and state transitions are part of the real domain behavior rather than an incidental implementation detail.

Java-facing public model without Kotlin-only surprises:

```kotlin
class ReceiptFormatter {
    @JvmOverloads
    fun format(amount: Long, currency: String = "USD"): String {
        return "$currency $amount"
    }
}
```

Use when: Java callers should be able to use the public API without hidden default-parameter assumptions.

Avoid:

- using `value class` when the type really has several fields or richer state transitions
- using `enum class` when each branch needs meaningful payload data or different behavior contracts
- using `data class` for entities whose identity is not pure field equality
- using sealed hierarchies when the set is expected to grow outside the module
- using singleton `object` just to avoid dependency injection or lifecycle clarity
