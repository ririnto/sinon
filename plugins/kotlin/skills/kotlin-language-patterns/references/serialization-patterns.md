---
title: Serialization Boundaries
description: >-
  Open this when kotlinx.serialization configuration or serializer behavior is the blocker.
---

Use this reference when the job is to implement Kotlin serialization for one boundary or fix serializer behavior that is already blocking the task. This reference should be sufficient on its own for that work.

Use this file to finish one of these jobs:

- create a working `@Serializable` model and one configured `Json` instance
- reason about defaults, required fields, and `@Transient`
- keep time fields consistent across a serialized boundary
- add a contextual serializer when the default format is not enough

Configured `Json` instance:

```kotlin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

@Serializable
data class Note(
    val title: String,
    val createdAt: Instant,
    val dueDate: LocalDate? = null,
)

val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

val encoded = json.encodeToString(
    Note(
        title = "ship-skill",
        createdAt = Clock.System.now(),
        dueDate = LocalDate.parse("2026-04-20"),
    ),
)

val decoded = json.decodeFromString<Note>(encoded)
```

Use one configured `Json` instance when the module talks to external APIs or wants stable formatting rules instead of scattering ad hoc defaults.

Important rules:

- missing required fields fail deserialization unless the property has a default value
- default values are not encoded by default
- `@Transient` properties need a default value
- only properties with backing fields are serialized
- use `kotlinx.datetime.Instant` for precise timestamps on the Kotlin 1.9 baseline; stdlib Instant requires a newer Kotlin baseline

Instant note:

- prefer `kotlinx.datetime.Instant` in Kotlin 1.9 models that need portable date-time serialization
- use one timestamp representation per boundary instead of mixing `kotlinx.datetime.Instant`, newer stdlib Instant, and `java.time.Instant` casually

Contextual serializer shape:

```kotlin
@Serializable
data class Event(@Contextual val instant: Instant)

val module = SerializersModule {
    contextual(InstantComponentSerializer)
}

val json = Json { serializersModule = module }
```

Use contextual serializers only when the default ISO-string or built-in representation is not enough and the module needs a custom format contract.
