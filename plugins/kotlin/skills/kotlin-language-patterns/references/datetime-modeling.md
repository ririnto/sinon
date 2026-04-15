---
title: Date-Time Modeling Guide
description: >-
  Reference for choosing Kotlin and kotlinx-datetime types, conversion timing, and civil-time modeling boundaries.
---

Use this reference when the job is to choose or convert Kotlin date-time types for one concrete model or workflow. This reference should be sufficient on its own for that task.

Use this file to finish one of these jobs:

- choose between `Instant`, `LocalDate`, and `LocalDateTime`
- decide when a civil-time value should stay local instead of becoming an `Instant`
- convert between scheduled local time and a zoned instant deliberately
- keep Kotlin stdlib time support and `kotlinx-datetime` responsibilities separate

Date-time rules:

- use `kotlin.time.Instant` for a real moment in time
- use `LocalDate` for a date-only concept such as a birthday or due date
- use `LocalDateTime` for civil or scheduled wall-clock concepts and keep the `TimeZone` separately
- do not convert far-future scheduled civil times into `Instant` too early because time-zone rules can change
- keep `kotlinx-datetime` for `LocalDate`, `LocalDateTime`, and `TimeZone` even when `Instant` and `Clock` come from the Kotlin standard library

Migration note:

- newer `kotlinx-datetime` releases move `Instant` and `Clock` usage toward `kotlin.time.*`
- current `kotlinx-datetime` releases keep type aliases to ease migration, but new code should prefer `kotlin.time.Instant` and `Clock.System.now()`

Example:

```kotlin
import kotlin.time.Clock
import kotlin.time.Instant
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
