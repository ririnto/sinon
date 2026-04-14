---
title: Serialization Patterns Guide
description: >-
  Reference for kotlinx.serialization configuration, serializer behavior, and timestamp modeling choices in Kotlin code.
---

Use this reference when the common JSON encode/decode path is clear and the remaining blocker is serializer behavior or format configuration.

Configured `Json` instance:

```kotlin
val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}
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
