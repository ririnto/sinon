---
title: Type Shape Decisions
description: >-
  Open this when choosing among Kotlin type shapes is still ambiguous after the common path.
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

The canonical type-shape examples (`value class`, `data class`, `object`, `enum class`, sealed types, regular class) and their decision criteria are in `SKILL.md` under "Choose the smallest type shape". This reference covers only additive material not present there.

## Operator Conventions

Define operator functions to enable symbolic syntax for domain-appropriate operations. Only overload when the meaning is obvious within the domain.

```kotlin
data class Vector(val x: Double, val y: Double) {
    operator fun plus(other: Vector): Vector = Vector(x + other.x, y + other.y)
    operator fun times(scalar: Double): Vector = Vector(x * scalar, y * scalar)
}

val sum = Vector(1.0, 2.0) + Vector(3.0, 4.0)

data class Grid(private val cells: Array<Array<Int>>) {
    operator fun get(row: Int, col: Int): Int = cells[row][col]
    operator fun set(row: Int, col: Int, value: Int) { cells[row][col] = value }
}

val value = grid[0, 1]
grid[0, 1] = 42

class RouteHandler(private val routes: Map<String, () -> Unit>) {
    operator fun invoke(path: String) {
        routes[path]?.invoke() ?: println("404: $path")
    }
}
```

Common conventions: `+`/`-` for algebraic composition, `get`/`set` for indexed access, `invoke` for callable abstractions, `compareTo` for ordering. Never overload for side-effect-heavy or non-obvious meanings.

## Functional Interfaces (SAM)

Use `fun interface` when you need a Kotlin-native single-abstract-method interface that allows lambda syntax at call sites. Unlike Java SAM conversion (covered in `SKILL.md`), `fun interface` is a Kotlin-first construct that works without a JVM runtime target.

```kotlin
fun interface ClickHandler {
    fun onClick(event: ClickEvent)
}

fun setupButton(handler: ClickHandler) { }

setupButton { event -> println("Clicked at ${event.x},${event.y}") }
```

Use `fun interface` when the callback contract is Kotlin-defined and you want lambda syntax without pulling in a Java interface. For Java interfaces consumed from Kotlin, SAM conversion applies automatically -- no `fun interface` needed.
