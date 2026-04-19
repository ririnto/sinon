---
name: kotlin-language-patterns
description: >-
  Use this skill when the user asks to "write idiomatic Kotlin", "refactor Java to Kotlin", "use Kotlin null safety", "choose data class vs sealed class", "use Kotlin serialization", "model Path or date-time values in Kotlin", "choose collection vs sequence", "use scope functions", "use runCatching or Result", "parse text with Regex", or needs guidance on Kotlin language and standard-library patterns.
metadata:
  title: "Kotlin Language Patterns"
  official_project_url: "https://kotlinlang.org/"
  reference_doc_urls:
    - "https://kotlinlang.org/docs/null-safety.html"
    - "https://kotlinlang.org/docs/classes.html"
    - "https://kotlinlang.org/docs/data-classes.html"
    - "https://kotlinlang.org/docs/object-declarations.html"
    - "https://kotlinlang.org/docs/sealed-classes.html"
    - "https://kotlinlang.org/docs/inline-classes.html"
    - "https://kotlinlang.org/docs/extensions.html"
    - "https://kotlinlang.org/docs/collections-overview.html"
    - "https://kotlinlang.org/docs/sequences.html"
    - "https://kotlinlang.org/docs/scope-functions.html"
    - "https://kotlinlang.org/docs/strings.html"
    - "https://kotlinlang.org/docs/coding-conventions.html"
    - "https://kotlinlang.org/docs/serialization.html"
    - "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/"
    - "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.text/-regex/"
    - "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/-result/"
    - "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin/run-catching.html"
    - "https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.io.path/"
    - "https://kotlinlang.org/docs/java-interop.html"
    - "https://github.com/Kotlin/kotlinx-datetime"
---

## Goal

Write idiomatic Kotlin by choosing the smallest language construct or stdlib path that keeps meaning obvious. Keep the common path focused on null safety, type modeling, extensions, collection shaping, string handling, boundary error flow, Java interop, and Kotlin-native boundary choices such as serialization, date-time, and JVM filesystem paths, then open a blocker reference only when deeper modeling or adjacent platform topics actually matter.

## Operating Rules

- MUST keep nullability explicit in public APIs.
- SHOULD prefer `val` by default.
- SHOULD choose the smallest type shape that matches the domain.
- SHOULD prefer direct string helpers before introducing `Regex`.
- SHOULD keep collection pipelines eager by default and move to `Sequence` only when laziness materially helps.
- SHOULD use `runCatching` and `Result` at parsing, I/O, or integration boundaries rather than ordinary local business flow.
- MUST preserve Java interoperability requirements when they matter.
- MUST call out JVM-only or experimental APIs inline instead of treating them as unconditional defaults.
- SHOULD keep class members in a stable scan order so the public shape stays predictable.

## Common-Path Procedure

1. Read the target type, function, or call site before changing syntax.
2. Decide whether the main issue is null safety, type shape, extension placement, collection shape, text parsing, boundary error handling, Java interop, or a Kotlin-native boundary such as serialization, date-time, or filesystem paths.
3. Start with the smallest Kotlin default: explicit nullable flow, direct collections, direct string helpers, and ordinary control flow.
4. Upgrade only when the contract clearly needs a stronger construct such as a `value class`, sealed hierarchy, `Sequence`, `Regex`, or `Result` boundary.
5. Open one blocker reference only when the remaining problem is deeper modeling ambiguity, null-and-scope-function cleanup, sequence tradeoffs, or an adjacent ecosystem boundary.

## Core Decisions

### Null safety first

Model absence directly and keep the flow readable.

```kotlin
fun primaryEmail(user: User?): String? =
    user?.emails?.firstOrNull { it.isPrimary }?.value
```

Use early returns, `?.`, `?:`, and `as?` before reaching for `!!`.

### Choose the smallest type shape

Use a `value class` for one wrapped domain value, a `data class` for immutable value carriers, a regular `class` for stateful behavior, an `object` for singleton behavior, and sealed modeling when the variant set is intentionally closed.

```kotlin
@JvmInline
value class CustomerId(val value: String)

data class Customer(val id: CustomerId, val name: String)

sealed interface PaymentResult {
    data class Approved(val authorizationId: String) : PaymentResult
    data class Rejected(val reason: String) : PaymentResult
}
```

### Use extensions as local language tools

Use extensions when they make call sites clearer without hiding ownership or dispatch. Remember that members win over extensions and that extension dispatch is static.

```kotlin
fun String.normalizedIssueKey(): String = trim().uppercase()
```

### Collections before `Sequence`

Prefer ordinary collections for finite in-memory work. Move to `Sequence` only when laziness or single-pass processing materially improves the path.

```kotlin
fun activeIds(customers: List<Customer>): List<CustomerId> =
    customers.filter { it.active }.map { it.id }
```

### Scope functions by intent

Use scope functions only when they make ownership or transformation clearer: `let` for nullable handoff, `apply` for receiver configuration, `also` for side-effect steps, `run` for scoped computation, and `with` for receiver-heavy code you already own. Stop when nesting makes the path harder to scan than named locals.

### String helpers before `Regex`

Start with `trim`, `substringBefore`, `substringAfter`, `startsWith`, `split`, or `lineSequence`. Use `Regex` only when pattern matching is the real requirement.

```kotlin
private val issuePattern = Regex("""([A-Z]+)-(\d+)""")

fun parseIssueKey(input: String): Pair<String, Int>? {
    val trimmed = input.substringBefore('?').trim()
    val match = issuePattern.matchEntire(trimmed) ?: return null
    val (project, number) = match.destructured
    return project to number.toInt()
}
```

### `Result` and `runCatching` at boundaries

Capture failures at parsing, I/O, or integration edges. Do not thread `Result` through every local branch of business logic.

```kotlin
fun parsePort(raw: String): Result<Int> =
    runCatching { raw.trim().toInt() }
        .mapCatching { port ->
            require(port in 1..65_535)
            port
        }
```

### Keep Java callers in view

If Java calls the API, avoid surprising Kotlin-only assumptions around default parameters, nullability, and naming.

```kotlin
class OrderFormatter {
    @JvmOverloads
    fun format(orderId: String, uppercase: Boolean = false): String {
        return if (uppercase) orderId.uppercase() else orderId
    }
}
```

### Keep Kotlin-native boundaries explicit

Keep adjacent Kotlin-native boundaries in this skill even when their detailed implementation moves to references.

- use `kotlinx.serialization` when the boundary is Kotlin-first model encoding or decoding
- use `kotlin.time.Instant` for real moments in time, and keep `LocalDate`, `LocalDateTime`, and `TimeZone` in `kotlinx-datetime`
- use `java.nio.file.Path` plus `kotlin.io.path.*` on JVM when filesystem semantics matter more than raw strings

### Keep member ordering predictable

When one file defines a class with companion members, overrides, helper methods, and nested types, keep the ordinary scan order stable: static-like companion members first, then instance properties, constructors, companion methods, overridden methods, instance methods, and finally nested types.

```kotlin
class Example(private val value: String) {
    companion object {
        private const val TYPE = "example"

        fun of(value: String): Example = Example(value)
    }

    override fun toString(): String = value

    fun value(): String = value

    private class Parser
}
```

This follows the Kotlin coding-conventions expectation that class contents stay easy to scan instead of drifting into arbitrary order.

## First Safe Default

If the path is still unclear, start from one explicit nullable flow and one direct collection transformation.

```kotlin
@JvmInline
value class UserId(val value: Long)

data class User(val id: UserId, val active: Boolean)

fun activeUserIds(users: List<User>): List<UserId> =
    users.filter { it.active }.map { it.id }
```

## Validate the Result

Check these pass/fail conditions before you stop:

- nullability is explicit and `!!` is not acting as a design shortcut
- the chosen type shape matches the domain meaning instead of syntax fashion
- extensions improve the call site without hiding ownership rules
- collection code stays eager unless laziness materially helps
- scope functions are readable in one pass and could not be replaced more clearly by named locals
- direct string helpers were considered before `Regex`
- `Result` stays at boundaries rather than infecting ordinary business flow
- Java callers are not surprised by hidden Kotlin assumptions
- serialization, date-time, and JVM path choices stay explicit instead of being silently pushed into unrelated plugins
- member ordering still leaves one file easy to scan from top to bottom

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| using `!!` as a design shortcut | null-safety turns into hidden runtime failure | model absence explicitly |
| using raw `String` or `Long` for meaningful IDs everywhere | domain meaning gets weaker | use a `value class` when one wrapped value has real semantic weight |
| converting every pipeline to `asSequence()` | laziness adds noise to small in-memory code | keep collections by default |
| using `Regex` for fixed delimiters or prefixes | parsing gets heavier than the real requirement | start with string helpers |
| nesting scope functions until the receiver becomes unclear | ownership and flow become hard to scan | use named locals or early returns |
| threading `Result` through ordinary business logic | local code becomes wrapper-heavy | keep `Result` at the boundary |

## Output Contract

Return:

1. the chosen Kotlin shape and why it fits the job
2. any nullability, collection, or parsing decisions that affect behavior
3. any Java-interop caveats that still matter
4. any blocker references needed for deeper branches

## Blocker References

Open only the reference that matches the remaining blocker.

| Open when... | Read... |
| --- | --- |
| choosing among `value class`, `data class`, regular `class`, `object`, enum, and sealed modeling still feels ambiguous | `./references/language-modeling.md` |
| cleaning up a null-heavy path or tangled scope-function chain is the real blocker | `./references/null-safety-and-scope-functions.md` |
| deciding whether laziness is worth the cost or restructuring a pipeline around `Sequence` is the blocker | `./references/collections-and-sequences.md` |
| implementing a JVM filesystem boundary needs exact `Path`, resource, or large-file handling code | `./references/path-filesystem.md` |
| modeling a timestamp, date-only concept, or civil time needs exact conversion guidance | `./references/datetime-modeling.md` |
| implementing Kotlin serialization needs exact `Json`, default-value, or contextual-serializer guidance | `./references/serialization-patterns.md` |
| deciding whether a stdlib surface is common, JVM-only, experimental, or outside the normal path needs explicit caveats | `./references/stdlib-boundaries.md` |

## Scope Boundaries

Use this skill for Kotlin language and stdlib common-path work: null safety, type shape, extensions, collections, scope functions, string handling, `Result` boundaries, and Java interop basics.

It also owns Kotlin-native boundary choices for serialization, date-time modeling, JVM filesystem paths, and predictable member ordering when those questions are still Kotlin language or API-shape decisions.

Do not use this skill as the primary source for coroutine or Flow API design, Kotlin testing strategy, or runtime-specific diagnostics.
