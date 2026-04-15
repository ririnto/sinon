---
name: kotlin-language-patterns
description: >-
  This skill should be used when the user asks to "write idiomatic Kotlin", "refactor Java to Kotlin", "use Kotlin null safety", "choose data class vs sealed class", "use Kotlin serialization", "model Path or date-time values in Kotlin", "choose collection vs sequence", "use runCatching or Result", "parse text with Regex", or needs guidance on Kotlin language and standard-library patterns.
---

# Kotlin Language Patterns

## Overview

Use this skill to write idiomatic Kotlin with clear null-safety, value modeling, stdlib collection shaping, text handling, boundary-aware error flow, serialization setup, date-time choices, filesystem boundaries, and Java interop. The common case is choosing the smallest Kotlin construct or stdlib path that keeps meaning obvious, then opening references only when JVM-only, serialization, date-time, or deeper stdlib details actually matter.

## Use This Skill When

- You are choosing between `data class`, regular `class`, `object`, enum, or sealed modeling.
- You are refactoring nullable or collection-heavy Kotlin code.
- You are deciding whether a domain value should be a `value class`, `String`, `Path`, `Instant`, or `LocalDate`-style type.
- You are deciding whether a collection pipeline should stay eager or move to `Sequence`.
- You are deciding whether simple string helpers are enough or `Regex` is the right tool.
- You are deciding whether boundary-style error capture should use `runCatching` and `Result`.
- You are adding `kotlinx.serialization` or `kotlinx-datetime` to a Kotlin module and need the smallest correct setup.
- You are reviewing whether a Kotlin API still reads correctly to Java callers.
- Do not use this skill when coroutine or Flow async semantics are the main question rather than Kotlin language and API shape.

## Common-Case Workflow

1. Read the target type, function, or call site before changing syntax.
2. Identify whether the real issue is type shape, null-safety, collection shape, stdlib boundary choice, serialization boundary, filesystem boundary, date-time meaning, or Java interop.
3. Keep the common stdlib path first: collections before `Sequence`, direct string helpers before `Regex`, and ordinary control flow before `Result` pipelines.
4. Apply the smallest Kotlin construct or library setup that makes the model clearer than the current Java-style form.
5. Open a reference only when the blocker is deeper modeling detail, JVM-only APIs, serializer behavior, date-time conversion, or other optional stdlib depth.

## Kotlin Stdlib Decision Path

Use this quick decision path before reaching for deeper references or extra libraries:

- start with ordinary collections; move to `Sequence` only when laziness or single-pass processing materially helps
- start with string helpers such as `substringBefore`, `substringAfter`, `lineSequence`, and `trim`; use `Regex` only when pattern matching is the real requirement
- use `runCatching` and `Result` at I/O, parsing, or integration boundaries; do not thread `Result` through every local branch of business logic
- use `readText` and `readLines` for normal-sized inputs; move to `forEachLine` or `useLines` when the file may be large
- use `Path` only when filesystem semantics matter, and call out JVM-only helpers inline
- use `kotlin.time.Instant` for real moments in time; keep `LocalDate`, `LocalDateTime`, and `TimeZone` in `kotlinx-datetime`
- use delegates or less-common stdlib surfaces only when the runtime behavior is intentional and the common path is no longer enough

## Minimal Setup

Add setup only when the module really needs serialization or date-time support. `Path` support is JVM-only and uses the Kotlin stdlib `kotlin.io.path` extensions on top of `java.nio.file.Path`, so it does not need an extra dependency.

Gradle JVM baseline with optional Kotlin-first data support:

```kotlin
plugins {
    kotlin("jvm") version "KOTLIN_VERSION"
}

repositories {
    mavenCentral()
}

dependencies {
    // add only what the module actually needs
}
```

Setup rules:

- add `kotlin("plugin.serialization")` only when the module serializes Kotlin models
- add `kotlinx-serialization-json` only when JSON encode/decode is actually required
- add `kotlinx-datetime` only when the model needs `LocalDate`, `LocalDateTime`, or `TimeZone`
- match `kotlin("plugin.serialization")` to the Kotlin compiler/plugin version
- treat `kotlinx-serialization-json` and `kotlinx-datetime` versions as normal runtime library versions
- use `kotlin.time.Instant` and `kotlin.time.Clock` for precise moments when the baseline supports them cleanly
- keep `kotlinx-datetime` for `LocalDate`, `LocalDateTime`, and `TimeZone`
- import `kotlin.io.path.*` only in JVM code that genuinely needs filesystem semantics

## First Runnable Commands or Code Shape

Start from one clear value model and one clear nullable flow:

```kotlin
object CustomerEmails {
    @JvmInline
    value class CustomerId(val value: String)

    fun primaryEmail(user: User?): String? =
        user?.emails
            ?.firstOrNull { it.isPrimary }
            ?.value
}
```

Use when: cleaning up Java-style Kotlin or needing a default idiomatic baseline.

Then add the smallest stdlib pattern that matches the real need:

```kotlin
data class Customer(val id: String, val active: Boolean)

fun activeIds(customers: List<Customer>): List<String> =
    customers
        .filter { it.active }
        .map { it.id }
```

Use when: the pipeline is finite, already in memory, and still readable in one pass.

## Ready-to-Adapt Templates

These templates stay in `SKILL.md` because they are part of the skill's primary purpose and should be available without opening references.

Inline value model:

```kotlin
@JvmInline
value class CustomerId(val value: String)
```

Use when: the type wraps exactly one primitive or string-like value and the goal is type safety plus domain meaning.

Immutable value model:

```kotlin
data class Customer(
    val id: CustomerId,
    val name: String,
)
```

Use when: the type is a multi-field value carrier rather than a mutable entity or service object.

Closed variant model:

```kotlin
sealed interface PaymentResult {
    data class Approved(val authorizationId: String) : PaymentResult
    data class Rejected(val reason: String) : PaymentResult
}
```

Use when: the set of variants is intentionally closed inside the module.

Java-friendly API shape:

```kotlin
class OrderFormatter {
    @JvmOverloads
    fun format(orderId: String, uppercase: Boolean = false): String {
        return if (uppercase) orderId.uppercase() else orderId
    }
}
```

Use when: the API is called from both Kotlin and Java code.

Predictable member ordering:

```kotlin
class Example(
    private val value: String,
) {
    companion object {
        private const val TYPE = "example"

        fun of(value: String): Example = Example(value)
    }

    override fun toString(): String = value

    fun value(): String = value

    private class Parser
}
```

Use when: an ordinary Kotlin class needs a stable top-to-bottom scan order for companion members, properties, overrides, methods, and nested types.

Collections first, `Sequence` only when laziness helps:

```kotlin
fun parseEnabledNames(lines: List<String>): List<String> =
    lines
        .asSequence()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }
        .take(100)
        .toList()
```

Use when: the source is large enough or the chain is selective enough that lazy, single-pass processing materially improves the path.

Text helper first, `Regex` when pattern matching is real:

```kotlin
private val issuePattern = Regex("""([A-Z]+)-(\d+)""")

fun parseIssueKey(input: String): Pair<String, Int>? {
    val trimmed = input.substringBefore('?').trim()
    val match = issuePattern.matchEntire(trimmed) ?: return null
    val (project, number) = match.destructured
    return project to number.toInt()
}
```

Use when: fixed delimiters are not enough and the input shape is actually pattern-driven.

Boundary error capture with `runCatching`:

```kotlin
fun parsePort(raw: String): Result<Int> =
    runCatching { raw.trim().toInt() }
        .mapCatching { port ->
            require(port in 1..65_535)
            port
        }
```

Use when: parsing or integration code needs explicit success or failure at the boundary.

Minimal serialization shape:

```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val title: String,
    val done: Boolean = false,
)
```

Use when: the module needs Kotlin-first serialization and the remaining question is just the default model shape rather than serializer configuration.

## Validate the Result

Validate the common case with these checks:

- the type shape matches the domain meaning rather than syntax fashion
- nullability stays explicit in public APIs
- single-value domain wrappers use a `value class` when that meaning is stronger than a raw primitive or `String`
- collection pipelines stay eager by default and use `Sequence` only when laziness helps enough to justify it
- simple text shaping stays with direct string helpers before `Regex` is introduced
- `runCatching` and `Result` stay at parsing, I/O, or integration boundaries instead of replacing ordinary local control flow
- serialization setup includes both the Kotlin serialization plugin and a runtime format dependency when JSON is required
- file boundaries use `Path` when filesystem semantics matter instead of stringly-typed paths
- timestamps and calendar dates are not collapsed into one vague date-time type
- JVM-only or experimental stdlib APIs are called out inline rather than presented as if they were unconditional common-path tools
- Java callers are not surprised by Kotlin-only assumptions such as hidden default parameters
- long scope-function chains are still readable in one pass

## Deep References

Open the single reference that matches the blocker. Each reference is intended to help complete one specific job, so you should not need to chain through multiple documents just to answer one question.

| Open when... | Read... |
| --- | --- |
| choosing among value class, data class, regular class, object, enum, and sealed modeling still needs more than the default templates | `./references/language-modeling.md` |
| cleaning up a null-heavy or collection-heavy function needs a fuller rewrite strategy, including delegate-heavy state handling | `./references/null-collection-rewrite.md` |
| implementing a JVM filesystem task needs exact `Path`, resource, or large-file handling code | `./references/path-filesystem.md` |
| modeling a real timestamp, date-only concept, or scheduled civil time needs exact conversion guidance | `./references/datetime-modeling.md` |
| implementing Kotlin serialization needs exact `Json`, default-value, or contextual-serializer guidance | `./references/serialization-patterns.md` |
| deciding whether a stdlib surface is common, JVM-only, experimental, or outside the normal path needs explicit caveats | `./references/stdlib-boundaries.md` |

## Invariants

- SHOULD prefer `val` by default.
- MUST keep nullability explicit in public APIs.
- SHOULD prefer `value class` for single-field domain wrappers when stronger typing matters.
- SHOULD prefer `data class` for immutable value carriers.
- SHOULD keep collection pipelines eager by default and move to `Sequence` only when laziness materially helps.
- SHOULD prefer direct string helpers before introducing `Regex`.
- SHOULD keep `runCatching` and `Result` at parsing, I/O, or integration boundaries instead of ordinary in-memory branching.
- MUST match the Kotlin serialization plugin version to the Kotlin compiler/plugin version.
- SHOULD configure one reusable `Json` instance instead of scattering ad hoc JSON behavior.
- MUST not force Java-style verbosity where Kotlin offers a clearer equivalent.
- MUST preserve Java interoperability requirements when they matter.
- MUST call out JVM-only or experimental stdlib APIs inline when they appear in canonical examples.
- SHOULD order class members as: companion-object or other static-like fields, instance properties, constructors, companion-object methods, overridden methods, instance methods, then nested classes/objects/enums.
- SHOULD order declarations within each method group by visibility: `public`, `protected`, internal/package-level equivalent, then `private`.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| replacing every explicit type with inference | scanning gets harder and intent gets weaker | keep explicit types where they improve readability |
| using raw `String` or `Long` for meaningful IDs everywhere | domain meaning and type safety get weaker | use a `value class` when one wrapped value has real semantic weight |
| adding `kotlinx-serialization-json` without the Kotlin serialization plugin | serializers are not generated and runtime errors appear | add both the compiler plugin and the runtime dependency |
| converting every collection chain to `asSequence()` by default | laziness adds noise and can make a small in-memory path harder to read | keep ordinary collections by default and switch to `Sequence` only when laziness materially helps |
| using `Regex` for a fixed delimiter or prefix check | the code gets heavier than the real parsing need | start with `substringBefore`, `substringAfter`, `startsWith`, `split`, or `lineSequence` |
| threading `Result` through ordinary business logic | local code becomes wrapper-heavy and control flow harder to scan | keep `Result` at parsing, I/O, or integration boundaries and unwrap into a normal domain model when appropriate |
| using `!!` as a design shortcut | null-safety becomes hidden runtime failure | model absence explicitly and keep the flow nullable |
| presenting JVM-only stdlib helpers as if they were common multiplatform defaults | callers copy the example into the wrong runtime boundary | label JVM-only APIs inline and keep the common path multiplatform by default |
| scattering companion members, overrides, helper methods, and nested types without a stable order | the class stops being quickly scannable during normal Kotlin authoring | keep the ordinary member-order baseline in the main skill and apply it consistently |

## Scope Boundaries

- Activate this skill for:
  - Kotlin type and API shape decisions
  - Kotlin stdlib collection, text, and `Result` patterns
  - Kotlin serialization, date-time, and JVM path boundary choices
  - null-safety and collection readability
  - Java interop boundaries in Kotlin code
- Do not use this skill as the primary source for:
  - coroutine or Flow API design
  - Kotlin testing strategy
  - Java/JDK-specific tooling or runtime diagnostics
