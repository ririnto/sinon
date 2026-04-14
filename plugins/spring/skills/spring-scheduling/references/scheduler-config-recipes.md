---
title: Spring Scheduler Configuration Recipes
description: >-
  Reference for explicit TaskScheduler wiring, SchedulingConfigurer use-cases, and trigger-programmatic scenarios.
---

Use this reference when the scheduling mode is chosen and the remaining work is scheduler bean or trigger wiring.

## When to Use Explicit TaskScheduler Wiring

Favor Boot's `spring.task.scheduling` pool properties when pool size or thread naming is the only concern. Reach for an explicit `ThreadPoolTaskScheduler` bean when:

- the scheduler must be injected into other beans directly
- the pool configuration exceeds what `spring.task.scheduling` expresses (for example, rejection handlers or custom thread factories)
- the application registers tasks programmatically against a known scheduler instance

## When to Use SchedulingConfigurer

`SchedulingConfigurer` is warranted when:

- trigger registration must stay programmable instead of annotation-only
- the schedule expression comes from an external source such as a database or ConfigMap at startup
- multiple tasks share one registrar and the configuration lives in one place rather than scattered across `@Scheduled` annotations
- a test or integration context needs to register tasks without classpath scanning

## When to Use Trigger API Instead of @Scheduled

The `addTriggerTask` overload on `ScheduledTaskRegistrar` is the right tool when:

- the next execution time is calculated from a `TriggerContext` instead of a fixed expression
- the schedule is dynamic within a run and cannot be expressed as a static cron string
- you need `CronTrigger` with an explicit `TimeZone` that differs from the system default

Do not reach for programmatic trigger wiring to gain dynamic registration if `@Scheduled` with a `SpEL` expression driven from a property would suffice; the annotation path is simpler and the property drives the same effect.
