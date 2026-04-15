---
title: Serialization Patterns Guide
description: >-
  Reference for kotlinx.serialization configuration, serializer behavior, and timestamp modeling choices in Kotlin code.
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
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlin.time.Clock
import kotlin.time.Instant

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
- `kotlin.time.Instant` works as the preferred precise timestamp type on current Kotlin baselines, while `kotlinx-datetime` still owns `LocalDate`, `LocalDateTime`, and `TimeZone`

Instant note:

- prefer `kotlin.time.Instant` in new models when the module baseline already supports it cleanly
- use one timestamp representation per boundary instead of mixing `kotlin.time.Instant`, `kotlinx.datetime.Instant`, and `java.time.Instant` casually

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
