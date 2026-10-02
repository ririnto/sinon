---
description: >-
  Open this when kotlinx.serialization configuration or serializer behavior is the blocker.
---

# Serialization Boundaries

Set up a serialization boundary or diagnose serializer behavior:

- create a working `@Serializable` model and one configured `Json` instance
- reason about defaults, required fields, and `@Transient`
- keep time fields consistent across a serialized boundary
- add a contextual serializer when the default format is not enough

This adapted example requires Kotlin 2.3 or later and `kotlinx.serialization` 1.9.0 or later:

```kotlin
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
data class Note(
    val title: String,
    val createdAt: Instant,
    val dueDate: LocalDate? = null
)

val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

val encoded = json.encodeToString(
    Note(
        title = "ship-skill",
        createdAt = Clock.System.now(),
        dueDate = LocalDate.parse("2026-04-20")
    )
)

val decoded = json.decodeFromString<Note>(encoded)
```

Use one configured `Json` instance when the module talks to external APIs or wants stable formatting rules instead of scattering ad hoc defaults.

Important rules:

- missing required fields fail deserialization unless the property has a default value
- default values are not encoded by default
- `@Transient` properties need a default value
- only properties with backing fields are serialized
- use stable `kotlin.time.Instant` for precise timestamps on Kotlin 2.3 or later
  - Its built-in serializer requires `kotlinx.serialization` 1.9.0 or later.

Instant note:

- preserve the managed `0.6.x` or documented `0.7.x` compatibility release when an existing Kotlin 2.1 boundary needs `kotlinx.datetime.Instant`
- ordinary `kotlinx-datetime` 0.8.0 no longer provides `kotlinx.datetime.Instant` or `kotlinx.datetime.Clock`
- use one timestamp representation per boundary instead of mixing `kotlinx.datetime.Instant`, stdlib Instant, and `java.time.Instant`

Contextual serializer shape:
`InstantComponentSerializer` requires the `ExperimentalTime` opt-in even when the stdlib `Instant` type is stable.

```kotlin
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.InstantComponentSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
data class Event(@Contextual val instant: Instant)

@OptIn(ExperimentalTime::class)
val module = SerializersModule {
    contextual(Instant::class, InstantComponentSerializer)
}

val json = Json { serializersModule = module }
```

Use contextual serializers only when the default ISO-string or built-in representation is not enough and the module needs a custom format contract.
