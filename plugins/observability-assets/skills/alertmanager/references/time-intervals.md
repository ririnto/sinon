---
title: "Alertmanager Time Intervals"
description: "Open this when mute schedules, active time intervals, or time-based routing constraints are the blocker."
---

## Alertmanager Time Intervals

Use this reference when the blocker is time-based suppression, schedule-aware routing, or any use of `mute_time_intervals` / `active_time_intervals` on routes.

## Overview

Time intervals define named schedule windows. Routes reference them by name in two ways:

- `mute_time_intervals:` -- the route sends NO notifications during matching intervals
- `active_time_intervals:` -- the route ONLY matches alerts during matching intervals (outside these windows, the route behaves as if it does not exist)

Both fields accept a list of named interval references. Multiple intervals are ORed together: if ANY listed interval matches the current time, the condition activates.

## TimeIntervalSpec Schema

Each entry within a named `time_intervals:` block defines one schedule window with these fields:

| Field | Type | Description |
| --- | --- | --- |
| `times` | list of TimeRange | Time-of-day ranges; all must be satisfied (OR within the list) |
| `weekdays` | list of WeekdayRange | Day-of-week constraints; all must be satisfied (OR within the list) |
| `days_of_month` | list of DayOfMonthRange | Day-of-month constraints; all must be satisfied (OR within the list) |
| `months` | list of MonthRange | Month constraints; all must be satisfied (OR within the list) |
| **`years`** | list of YearRange | **Year constraints; all must be satisfied (OR within the list)** |
| `location` | string | IANA timezone name (e.g., `"America/New_York"`) |

**All specified fields are ANDed together**: an alert falls inside the interval only when it satisfies EVERY constraint type that is present. Omitted constraint types are unconstrained and always pass.

Within each field's list, multiple entries are ORed together: the alert passes if it matches ANY entry in the list.

## Sub-Field Types

### TimeRange

Defines a time-of-day window. End time is exclusive.

```yaml
times:
  - start_time: "09:00"
    end_time: "17:00"
```

| Property | Format | Range | Notes |
| --- | --- | --- | --- |
| `start_time` | `"HH:MM"` | `"00:00"` to `"24:00"` | Inclusive start |
| `end_time` | `"HH:MM"` | `"00:00"` to `"24:00"` | Exclusive end; `"24:00"` means end-of-day |

Validation: both required; `start_time` must be strictly less than `end_time`.

### WeekdayRange

Defines a day-of-week range using full lowercase names. Sunday = 0, Saturday = 6.

```yaml
weekdays:
  - monday:friday
  - saturday
```

This matches Monday through Friday plus Saturday.

Valid names: `sunday`, `monday`, `tuesday`, `wednesday`, `thursday`, `friday`, `saturday`.

Format: single day (`"monday"`) or colon-separated range (`"monday:friday"`). Start day must not be after end day.

### DaysOfMonthRange

Defines a day-of-month range. Supports positive (1-based from month start) and negative (from month end) indices.

```yaml
days_of_month:
  - 1:15
  - -3:-1
```

This matches the 1st through 15th of the month and the last 3 days of the month.

| Property | Range | Notes |
| --- | --- | --- |
| Positive values | 1 to 31 | Count from the first day of the month |
| Negative values | -1 to -31 | Count from the last day of the month (-1 = last day) |
| Zero | forbidden | Causes validation error |

Format: single integer (`"15"`) or colon-separated range (`"1:15"`). Negative ranges require both ends negative if the start is negative.

Note: Values beyond the actual days in a short month (e.g., day 30 in February) are clamped to the valid range at evaluation time.

### Months

Defines a month range using full lowercase names or integers. January = 1, December = 12.

```yaml
months:
  - january:march
  - october:december
```

This matches Q1 and Q4.

Valid names: `january`, `february`, `march`, `april`, `may`, `june`, `july`, `august`, `september`, `october`, `november`, `december`.

Format: single name/integer (`"march"` / `"3"`) or colon-separated range (`"march:may"` / `"3:5"`). Start must not exceed end.

### Years

Defines a year range using positive integers only.

```yaml
years:
  - 2024:2026
  - 2024
```

This matches 2024 through 2026 inclusive and the single year 2024.

Format: single integer (`"2024"`) or colon-separated range (`"2024:2026"`). Start must not exceed end.

### Location

IANA timezone identifier string. Controls which timezone is used for evaluating ALL other fields in this interval spec.

```yaml
location: America/New_York
location: Europe/London
location: Asia/Tokyo
```

If omitted, times are evaluated in UTC.

## Timezone and Coverage Pitfalls

Check timezone assumptions before reviewing the interval body:

- if the schedule is business-hour sensitive, set `location` explicitly instead of assuming UTC
- if multiple teams use different local schedules, prefer separate named intervals rather than one ambiguous shared interval
- review `24:00` boundaries carefully so overnight windows do not leave a one-minute hole or overlap unexpectedly
- DST transitions can cause unexpected window shifts; test intervals around transition dates

## Complete Examples

Business hours in Berlin timezone:

```yaml
time_intervals:
  - name: eu-business-hours
    time_intervals:
      - location: Europe/Berlin
        weekdays:
          - monday:friday
        times:
          - start_time: "09:00"
            end_time: "17:00"
```

Split-window offhours pattern (before and after business hours + weekends):

```yaml
time_intervals:
  - name: offhours
    time_intervals:
      - weekdays:
          - monday:friday
        times:
          - start_time: "00:00"
            end_time: "08:00"
          - start_time: "18:00"
            end_time: "24:00"
      - weekdays:
          - saturday
          - sunday
        times:
          - start_time: "00:00"
            end_time: "24:00"
```

Month-specific maintenance window (first 3 days of Q1 months):

```yaml
time_intervals:
  - name: q1-maintenance
    time_intervals:
      - months:
          - january:march
        days_of_month:
          - 1:3
        times:
          - start_time: "02:00"
            end_time: "06:00"
```

End-of-quarter patching window (last 2 days of March, June, September, December):

```yaml
time_intervals:
  - name: quarter-end-patching
    time_intervals:
      - months:
          - march
          - june
          - september
          - december
        days_of_month:
          - -2:-1
        times:
          - start_time: "22:00"
            end_time: "04:00"
```

Year-limited schedule (specific years only):

```yaml
time_intervals:
  - name: 2024-retirement-window
    time_intervals:
      - years:
          - 2024
      - years:
          - 2025
        months:
          - january:june
```

Combined complex example (business hours during specific months):

This example models fiscal-year business hours in `America/Chicago`, with a July-through-June month range, Monday-through-Friday coverage, and an `08:00` to `17:00` window.

```yaml
time_intervals:
  - name: fiscal-year-business-hours
    time_intervals:
      - location: America/Chicago
        months:
          - july:june
        weekdays:
          - monday:friday
        times:
          - start_time: "08:00"
            end_time: "17:00"
```

## Usage in Routes

### Mute Time Intervals

Notifications from this route are suppressed during the referenced intervals:

```yaml
route:
  receiver: staging-team
  matchers:
    - environment="staging"
  mute_time_intervals:
    - offhours
    - holidays
```

During `offhours` or `holidays`, alerts matching this route still group and wait normally, but no notification is sent. When the interval ends, pending notifications fire immediately if their grouping timers have elapsed.

### Active Time Intervals

The route only exists (matches alerts) during the referenced intervals:

```yaml
route:
  receiver: business-hours-only
  active_time_intervals:
    - business-hours
  matchers:
    - severity="warning"
```

Outside `business-hours`, this route does not match at all. Alerts that would have matched fall through to the next matching route or the root route default receiver.

## Review Focus

- verify the timezone and business-hour assumptions
- keep the interval name descriptive
- avoid using schedule-based muting where the alert itself should be downgraded or rerouted instead
- check that every interval referenced by a route is defined in the same config file
- ensure `days_of_month` negative indices behave as expected for short months
- confirm that `years` constraints do not silently expire your routing rules

## Version Note

- Treat `time_intervals` as a version-sensitive Alertmanager feature rather than a universal baseline.
- The deprecated `mute_time_intervals` top-level key (separate from per-route `mute_time_intervals`) still parses but will be removed before v1.0.
