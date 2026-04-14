---
title: Quartz Persistence and Clustering
description: >-
  Reference for JDBC-backed Quartz state, overwrite behavior, schema initialization, and advanced properties.
---

Use this reference when Quartz is already chosen and the remaining blocker is durable state or cluster-oriented operational configuration.

## Native Quartz Property Pass-Through

Use `spring.quartz.properties.*` when the required setting is a native Quartz property rather than a higher-level Boot shortcut:

```properties
spring.quartz.properties.org.quartz.scheduler.instanceName=appScheduler
spring.quartz.properties.org.quartz.jobStore.isClustered=true
spring.quartz.properties.org.quartz.scheduler.instanceId=AUTO
spring.quartz.properties.org.quartz.jobStore.misfireThreshold=60000
```

Key properties:

- `instanceName` identifies this scheduler in the job store and logs
- `isClustered=true` enables Quartz clustering features so multiple scheduler instances share the same job store
- `instanceId=AUTO` lets Quartz generate a unique instance ID
- `misfireThreshold` controls how late a trigger must be (in milliseconds) before Quartz treats it as misfired and applies the trigger misfire instruction

## Misfire Threshold Nuance

Setting `misfireThreshold` is essential when restart or downtime behavior matters:

- if a trigger fires at 6:00 AM and the scheduler is down until 6:30, misfireThreshold determines whether the 6:00 fire is treated as missed
- a lower threshold makes Quartz more aggressive about declaring misfires after brief interruptions
- per-trigger misfire instructions (such as `withMisfireHandlingInstructionDoNothing()` or `withMisfireHandlingInstructionFireAndProceed()`) interact with this global threshold

## Schema Initialization Risk

Standard Quartz schema scripts can drop existing tables. Review schema initialization carefully before using `initialize-schema=always` outside disposable environments.

## Clustering Notes

When `isClustered=true`:

- all scheduler instances must use the same job store database
- Quartz uses database-level locking to coordinate trigger firing across instances
- each instance must have the same `instanceName` prefix or `instanceId` configuration to avoid conflicts
- clustering does not automatically load-balance; it provides failover and state sharing
