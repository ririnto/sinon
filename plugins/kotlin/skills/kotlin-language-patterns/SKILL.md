---
name: kotlin-language-patterns
description: >-
  This skill should be used when the user asks to "write idiomatic Kotlin", "refactor Java to Kotlin", "use Kotlin null safety", "choose data class vs sealed class", "use Kotlin serialization", "model Path or date-time values in Kotlin", or needs guidance on Kotlin language and library patterns.
---

# Kotlin Language Patterns

## Overview

Use this skill to write idiomatic Kotlin with clear null-safety, value modeling, collection shaping, filesystem and date-time boundaries, serialization setup, and Java interop boundaries. The common case is turning Java-flavored Kotlin into a smaller, more explicit Kotlin shape without hiding meaning, then choosing the smallest library setup that matches the boundary. Start from the type and API shape first, then add serialization or JVM file APIs only where the domain actually needs them.

## Use This Skill When

- You are choosing between `data class`, regular `class`, `object`, enum, or sealed modeling.
- You are refactoring nullable or collection-heavy Kotlin code.
- You are deciding whether a domain value should be a `value class`, `String`, `Path`, `Instant`, or `LocalDate`-style type.
- You are adding `kotlinx.serialization` or `kotlinx-datetime` to a Kotlin module and need the smallest correct setup.
- You are reviewing whether a Kotlin API still reads correctly to Java callers.
- Do not use this skill when coroutine or Flow async semantics are the main question rather than Kotlin language and API shape.

## Common-Case Workflow

1. Read the target type, function, or call site before changing syntax.
2. Identify whether the real issue is type shape, null-safety, serialization boundary, filesystem boundary, date-time meaning, or Java interop.
3. Apply the smallest Kotlin construct or library setup that makes the model clearer than the current Java-style form.
4. Escalate to deeper reference material only if the blocker is version-sensitive setup, advanced serializer behavior, or edge-case language design.

## Minimal Setup

Add setup only when the module really needs serialization or date-time support. `Path` support is JVM-only and uses the Kotlin stdlib `kotlin.io.path` extensions on top of `java.nio.file.Path`, so it does not need an extra dependency.

Gradle JVM baseline:

```kotlin
plugins {
    kotlin("jvm") version "KOTLIN_VERSION"  // use the current stable version, e.g. from gradle.properties or your version catalog
    kotlin("plugin.serialization") version "KOTLIN_VERSION"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime")
}
```

Setup rules:

- match `kotlin("plugin.serialization")` to the Kotlin compiler/plugin version
- treat `kotlinx-serialization-json` and `kotlinx-datetime` versions as normal runtime library versions
- use `kotlin.time.Instant` and `kotlin.time.Clock` for precise moments, and keep `kotlinx-datetime` for `LocalDate`, `LocalDateTime`, and `TimeZone`
- import `kotlin.io.path.*` for JVM `Path` helpers such as `readText`, `writeText`, `name`, `extension`, and `createParentDirectories`

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
Use when: cleaning up Java-style Kotlin or need a default idiomatic baseline.

## Ready-to-Adapt Templates

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

Path-first file boundary:

```kotlin
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.extension
import kotlin.io.path.createParentDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class ConfigWriter {
    fun writeDefaultConfig(root: Path): Path {
        val out = root / "config" / "app.json"

        out.createParentDirectories()
        if (!out.exists()) {
            out.writeText("{}")
        }

        println(out.name)
        println(out.extension)
        println(out.readText())

        return out
    }
}
```
Use when: the value is a real filesystem location and path operations matter.

Date-time boundary:

```kotlin
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

data class Invoice(
    val issuedAt: Instant,
    val dueDate: LocalDate,
)
```
Use when: one field represents a precise timestamp and another represents a date-only business concept.

Serialization baseline:

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

@Serializable
data class Note(
    val title: String,
    val createdAt: Instant,
    val dueDate: LocalDate? = null,
)

val json = Json { ignoreUnknownKeys = true }

val encoded = json.encodeToString(
    Note(
        title = "ship-skill",
        createdAt = Clock.System.now(),
        dueDate = LocalDate.parse("2026-04-20"),
    ),
)

val decoded = json.decodeFromString<Note>(encoded)
```
Use when: the module needs JSON encode/decode with Kotlin-first models and common-case external API tolerance.

## Validate the Result

Validate the common case with these checks:

- the type shape matches the domain meaning rather than syntax fashion
- nullability stays explicit in public APIs
- single-value domain wrappers use a `value class` when that meaning is stronger than a raw primitive or `String`
- serialization setup includes both the Kotlin serialization plugin and a runtime format dependency when JSON is required
- file boundaries use `Path` when filesystem semantics matter instead of stringly-typed paths
- timestamps and calendar dates are not collapsed into one vague date-time type
- `Instant` is used for real moments in time, while `LocalDate` and `LocalDateTime` are used only for calendar or civil-time concepts
- Java callers are not surprised by Kotlin-only assumptions such as hidden default parameters
- long scope-function chains are still readable in one pass

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| choosing among value class, data class, object, enum, and sealed modeling in more detail | `./references/language-modeling.md` |
| deciding whether a nullable/collection pipeline is too clever | `./references/null-safety-collections.md` |
| serializer configuration, optional fields, unknown keys, or contextual serializers | `./references/serialization-patterns.md` |
| JVM `Path` helpers, file-boundary caveats, or `exists()` semantics | `./references/path-filesystem.md` |
| `Instant` vs `LocalDate` vs `LocalDateTime`, time-zone handling, or future schedule conversion | `./references/datetime-modeling.md` |

## Invariants

- SHOULD prefer `val` by default.
- MUST keep nullability explicit in public APIs.
- SHOULD prefer `value class` for single-field domain wrappers when stronger typing matters.
- SHOULD prefer `data class` for immutable value carriers.
- MUST match the Kotlin serialization plugin version to the Kotlin compiler/plugin version.
- SHOULD configure one reusable `Json` instance instead of scattering ad hoc JSON behavior.
- MUST not force Java-style verbosity where Kotlin offers a clearer equivalent.
- MUST preserve Java interoperability requirements when they matter.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| replacing every explicit type with inference | scanning gets harder and intent gets weaker | keep explicit types where they improve readability |
| using raw `String` or `Long` for meaningful IDs everywhere | domain meaning and type safety get weaker | use a `value class` when one wrapped value has real semantic weight |
| adding `kotlinx-serialization-json` without the Kotlin serialization plugin | serializers are not generated and runtime errors appear | add both the compiler plugin and the runtime dependency |
| stacking `let`, `also`, `run`, and `apply` in one path | control flow becomes clever instead of clear | use a named local or direct expression |
| introducing `sealed` hierarchies for open-ended models | future extension becomes awkward or wrong | use a regular class/interface when the set is not truly closed |
| passing filesystem paths around as ordinary strings | path joins, normalization, and platform semantics stay implicit | use `Path` at the boundary where filesystem meaning matters |
| using one date-time type for timestamps, local dates, and user-facing calendar concepts | time-zone and business semantics get blurred | choose `Instant`, `LocalDate`, or `LocalDateTime` based on the real domain meaning |
| converting future civil schedules into `Instant` too early | later time-zone rule changes can invalidate the intended wall-clock meaning | keep future schedule intent as `LocalDateTime` plus `TimeZone` until conversion is actually needed |
| using `!!` as a design shortcut | null-safety becomes hidden runtime failure | model absence explicitly and keep the flow nullable |

## Scope Boundaries

- Activate this skill for:
  - Kotlin type and API shape decisions
  - Kotlin serialization, date-time, and JVM path boundary choices
  - null-safety and collection readability
  - Java interop boundaries in Kotlin code
- Do not use this skill as the primary source for:
  - coroutine or Flow API design
  - Kotlin testing strategy
  - Java/JDK-specific tooling or runtime diagnostics
