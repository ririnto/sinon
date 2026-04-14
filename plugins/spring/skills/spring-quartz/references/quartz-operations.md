---
title: Quartz Operations Reference
description: >-
  Reference for dynamic registration, cancellation, pause/resume, and runtime monitoring with the Quartz Scheduler API.
---

Use this reference when Quartz is already chosen and the remaining blocker is live scheduler operations. Canonical code templates for registration, cancellation, pause/resume, and monitoring live in `SKILL.md`; this file holds misfire handling nuance only.

## Misfire Handling Nuance

Misfire behavior is one of the most operationally sensitive parts of Quartz scheduling. Key points:

### Global threshold

```properties
spring.quartz.properties.org.quartz.jobStore.misfireThreshold=60000
```

This global threshold (in milliseconds) determines how late a trigger must be before Quartz applies the per-trigger misfire instruction. A trigger that fires 30 seconds late will not be treated as misfired if `misfireThreshold=60000`.

### Per-trigger misfire instructions

Do not rely on Quartz defaults for misfire behavior. Set explicit instructions on each trigger:

- `withMisfireHandlingInstructionDoNothing()` - skip the missed fire entirely; do not replay
- `withMisfireHandlingInstructionFireAndProceed()` - fire immediately once when recovery begins
- `withMisfireHandlingInstructionIgnoreMisfires()` - Quartz will fire all missed triggers as soon as possible (can cause burst load)

### Cron trigger gotcha

Cron triggers with `withMisfireHandlingInstructionDoNothing()` will skip all missed fire times. If your cron expression only fires once per day and the scheduler was down for 3 days, only one fire occurs when it recovers, not three.

### Practical checklist

1. set `misfireThreshold` explicitly in production
2. choose misfire instruction per trigger based on whether missed work should be replayed or skipped
3. for business-critical scheduled work, prefer `DoNothing` or `FireAndProceed` over `IgnoreMisfires` to avoid burst load on recovery
4. test misfire behavior explicitly during outage simulations
