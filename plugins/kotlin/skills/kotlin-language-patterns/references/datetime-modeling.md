---
title: Date-Time Type Decisions
description: >-
  Open this when choosing or converting Kotlin date-time types is the blocker.
---

Use this reference when the job is to choose or convert Kotlin date-time types for one concrete model or workflow. This reference should be sufficient on its own for that task.

Use this file to finish one of these jobs:

- choose between `Instant`, `LocalDate`, and `LocalDateTime`
- decide when a civil-time value should stay local instead of becoming an `Instant`
- convert between scheduled local time and a zoned instant deliberately
- keep Kotlin 1.9 stdlib duration support and `kotlinx-datetime` date-time responsibilities separate

Date-time rules:

- use `kotlinx.datetime.Instant` for a real moment in time on the Kotlin 1.9 baseline
- use `LocalDate` for a date-only concept such as a birthday or due date
- use `LocalDateTime` for civil or scheduled wall-clock concepts and keep the `TimeZone` separately
- do not convert far-future scheduled civil times into `Instant` too early because time-zone rules can change
- keep `Instant`, `Clock`, `LocalDate`, `LocalDateTime`, and `TimeZone` in `kotlinx-datetime` unless the module intentionally raises its Kotlin baseline to one that supports stdlib Instant

Version note:

- Kotlin 1.9 does not provide stdlib Instant; use `kotlinx.datetime.Instant` with `org.jetbrains.kotlinx:kotlinx-datetime`
- when a project deliberately raises its Kotlin baseline to a version with stdlib Instant, keep one timestamp representation per boundary and migrate deliberately

Gradle dependency:

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
}
```

Example:

```kotlin
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

data class Invoice(
    val issuedAt: Instant,
    val dueDate: LocalDate,
)

val now: Instant = Clock.System.now()
val birthday = LocalDate.parse("2010-06-01")
val meeting = LocalDateTime.parse("2026-04-20T09:00:00")
val zone = TimeZone.of("Europe/Berlin")
val meetingInstant = meeting.toInstant(zone)
```

Use `toLocalDateTime(zone)` when a true instant needs to be shown as local civil time for one zone.
