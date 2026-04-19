---
title: "Alertmanager Time Intervals"
description: "Open this when mute schedules or active time intervals are the blocker."
---

# Alertmanager Time Intervals

Use this reference when the blocker is time-based suppression or schedule-aware routing rather than the route tree itself.

## Timezone and Coverage Pitfalls

Check timezone assumptions before reviewing the interval body:

- if the schedule is business-hour sensitive, set `location` explicitly instead of assuming UTC
- if multiple teams use different local schedules, prefer separate named intervals rather than one ambiguous shared interval
- review `24:00` boundaries carefully so overnight windows do not leave a one-minute hole or overlap unexpectedly

Explicit timezone pattern:

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

Split-window pattern for offhours before and after business hours:

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
```

## Review Focus

- verify the timezone and business-hour assumptions
- keep the interval name descriptive
- avoid using schedule-based muting where the alert itself should be downgraded or rerouted instead

## Version Note

- Treat `time_intervals` as a version-sensitive Alertmanager feature rather than a universal baseline.
