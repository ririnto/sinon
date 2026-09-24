---
description: >-
  Open this when choosing or converting Kotlin date-time types is the blocker.
---

# Date-Time Type Decisions

Choose a date-time type for one concrete model or workflow:

- choose between `Instant`, `LocalDate`, and `LocalDateTime`
- decide when a civil-time value should stay local instead of becoming an `Instant`
- convert between scheduled local time and a zoned instant deliberately
- keep Kotlin stdlib duration support and `kotlinx-datetime` date-time responsibilities separate

Date-time rules:

- use `kotlinx.datetime.Instant` for a real moment in time on the Kotlin 2.1 baseline
- use `LocalDate` for a date-only concept such as a birthday or due date
- use `LocalDateTime` for civil or scheduled wall-clock concepts and keep the `TimeZone` separately
- do not convert far-future scheduled civil times into `Instant` too early because time-zone rules can change
- keep `Instant`, `Clock`, `LocalDate`, `LocalDateTime`, and `TimeZone` in `kotlinx-datetime`
  unless the module raises its Kotlin baseline to 2.3+ to use stdlib `kotlin.time.Instant`

Version note:

- the Kotlin 2.1 baseline does not include stdlib Instant.
  - Stdlib `kotlin.time.Instant` is stable since Kotlin 2.3.
  - On the 2.1 baseline, use `kotlinx.datetime.Instant` with `org.jetbrains.kotlinx:kotlinx-datetime`.
- when a project deliberately raises its Kotlin baseline to 2.3+ for stdlib Instant, keep one timestamp representation per boundary.
  - Migrate deliberately.
- use the project's managed `kotlinx-datetime` version when it supports the required API.
  - For a new dependency or required upgrade, check Maven Central for the latest stable Kotlin-compatible release and record it in the project catalog.
  - Use a compatibility variant only when the project needs that variant's older API.

This executable sample shows the 0.8.0 artifact, not a current install target.
For a new installation, check Maven Central and Kotlin compatibility before recording the selected version in the project catalog.

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
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
    val dueDate: LocalDate
)

val now: Instant = Clock.System.now()
val birthday = LocalDate.parse("2010-06-01")
val meeting = LocalDateTime.parse("2026-04-20T09:00:00")
val zone = TimeZone.of("Europe/Berlin")
val meetingInstant = meeting.toInstant(zone)
```

Use `toLocalDateTime(zone)` when a true instant needs to be shown as local civil time for one zone.
