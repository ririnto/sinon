---
title: Spring Scheduling Runtime Task Control
description: >-
  Reference for dynamic registration, cancellation, replacement, and local visibility of Spring scheduled tasks.
---

Use this reference when the scheduling mode is already chosen and the remaining blocker is runtime control of in-process scheduled tasks.

## Dynamic Registration Rule

- use `TaskScheduler` when tasks must be added dynamically at runtime
- store the returned `ScheduledFuture<?>` under an explicit task identity

## Cancellation Rule

- cancel with `future.cancel(false)` when the task should stop being re-scheduled without interrupting active work
- remove the handle from the registry after cancellation succeeds

## Replacement Rule

- cancel the old task first, then register the replacement and store the new handle

## Visibility Rule

- use `ScheduledTaskHolder#getScheduledTasks()` for local inventory of Spring-managed scheduled tasks
- expose identifiers and schedule semantics through your own registry when business-level task names matter more than raw framework handles
