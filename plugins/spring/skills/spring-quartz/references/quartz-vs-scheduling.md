---
title: Quartz vs Spring Scheduling
description: >-
  Reference for deciding when Quartz is warranted instead of plain Spring scheduling.
---

Use this reference when the job already exists conceptually and the remaining blocker is whether Quartz is justified.

## Use plain Spring scheduling when

- the work is app-local
- persistence of trigger state is not required
- restart survival is not required
- one JVM instance can own the recurring work safely
- task identity can stay local to code and `ScheduledFuture` handles are enough for control

## Use Quartz when

- jobs or triggers must survive restart
- JDBC-backed state is required
- clustering-oriented Quartz properties matter
- trigger identity, calendars, or Quartz-specific scheduling features are part of the requirement
- jobs or triggers need stable runtime identities even before persistence is introduced
- operators need `Scheduler`-level actions such as pause/resume/delete/listing instead of only local task-handle cancellation
- one process may start in-memory today but still benefits from a scheduler registry model and future durability path

## Use ShedLock when

- the job can stay a plain Spring `@Scheduled` method but only one application instance should run it at a time
- durable trigger persistence is not required and a distributed lock is enough
- the team wants lighter operational complexity than a full Quartz job store

## In-Memory Quartz Still Makes Sense When

- the application needs to register and replace jobs by `JobKey` and `TriggerKey`
- registered jobs and triggers must be listed from one scheduler API
- pause/resume semantics are required as first-class operations
- current execution state must be queried through `getCurrentlyExecutingJobs()`
- the system needs richer scheduler semantics now, even if JDBC persistence can wait
