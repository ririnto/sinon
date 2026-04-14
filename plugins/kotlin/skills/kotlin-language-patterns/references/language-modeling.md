---
title: Language Modeling Guide
description: >-
  Reference for choosing Kotlin type shapes such as value classes, data classes, sealed hierarchies, and singleton objects.
---

Use this reference when the blocker is Kotlin type shape rather than syntax trivia.

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

Avoid:

- using `value class` when the type really has several fields or richer state transitions
- using `data class` for entities whose identity is not pure field equality
- using sealed hierarchies when the set is expected to grow outside the module
- using singleton `object` just to avoid dependency injection or lifecycle clarity
