---
title: Spring Scheduling Patterns
description: >-
  Reference for choosing scheduling modes and reasoning about overlap, time, and in-process job ownership.
---

Use this reference when the scheduled task exists conceptually and the remaining blocker is choosing the right scheduling mode.

## Mode Selection Rule

- use `fixedDelay` when the next run should wait until the previous run finishes and then wait the delay
- use `fixedRate` when the schedule is cadence-driven
- use `cron` when the schedule is calendar-based
- use `initialDelay` when the first execution should wait for startup or warmup conditions
- use `SchedulingConfigurer` or programmatic registration when trigger shape cannot be expressed cleanly as one fixed annotation

Important note:

- `initialDelay` applies to fixed-delay and fixed-rate scheduling, not cron expressions

## Scheduling Mode Examples

```java
// fixedDelay: wait for previous run to finish, then wait 5 seconds
@Scheduled(fixedDelay = 5000)
void pollLatest() { }

// fixedRate: run every 5 seconds regardless of how long the previous run took
// warning: if run time > interval, next run starts immediately after previous finishes (no skip)
@Scheduled(fixedRate = 5000)
void fixedRateTask() { }

// fixedDelayString: externalized delay value
@Scheduled(fixedDelayString = "${app.poll.interval-ms:5000}")
void configurableDelay() { }

// cron: business-hours schedule — every minute, 9am–5pm weekdays, US Eastern
@Scheduled(cron = "0 * * 9-17 ? * MON-FRI", zone = "America/New_York")
void businessHourTask() { }

// initialDelay + fixedDelay: wait 30s for warmup, then run every 10s
@Scheduled(initialDelay = 30_000, fixedDelay = 10_000)
void warmupThenPoll() { }
```

## ShedLock Example

ShedLock ensures a scheduled task runs on only one node even when multiple instances are deployed:

```java
// dependency
// implementation("net.javacrumbs.shedlock:shedlock-spring:5.10.0")

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
class ShedLockConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcLockProvider(dataSource);
    }
}
```

```java
@Scheduled(cron = "0 0 3 * * ?")  // runs daily at 3am
@SchedulerLock(name = "nightly_cleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
void nightlyCleanup() {
    // only one app instance executes this, even across a cluster
}
```

`lockAtMostFor` must be longer than the longest expected run to prevent early lock release on crashes; `lockAtLeastFor` prevents over-execution when runs are very short and the scheduler fires again quickly.

## Overlap Rule

- do not assume repeated schedules are automatically serialized in every configuration
- think in terms of pool size, run time, and what should happen if a prior invocation is still active

## Multi-Instance Rule

- plain `@Scheduled` runs independently on every application instance
- use ShedLock when only one node should run a scheduled task across multiple instances
- escalate to Quartz when distributed locking is not enough because restart survival, durable triggers, or richer scheduler lifecycle control are also required

## Failure Rule

- decide whether the job should log and continue, stop the app, or hand work to a more durable subsystem
- keep scheduled work idempotent where possible

## Time Rule

- attach a `zone` to cron expressions that represent business time
- keep environment-specific local time assumptions out of the code path
