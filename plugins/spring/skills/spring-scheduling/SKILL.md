---
name: spring-scheduling
description: >-
  Use this skill when the user asks to "use @Scheduled", "configure Spring scheduling", "run cron jobs in Spring", "use TaskScheduler", "register scheduled tasks dynamically", or needs guidance on lightweight Spring scheduling patterns.
---

# Spring Scheduling

## Overview

Use this skill to build lightweight in-process scheduled work with Spring’s scheduling support. The common case is one explicit scheduled method or one `TaskScheduler`-backed task with clear timing semantics, no hidden overlap assumptions, and one obvious failure policy. Use this for application-local recurring work, including dynamic registration and cancellation inside one JVM, not for durable cluster-wide scheduling state.

## Use This Skill When

- You are adding `@Scheduled` jobs to a Spring application.
- You need to choose between fixed delay, fixed rate, cron, or programmatic scheduling.
- You need a default `TaskScheduler` or `SchedulingConfigurer` shape.
- You need to dynamically register or cancel in-process scheduled tasks.
- You need to list registered tasks or make scheduled work observable with logs or metrics.
- You need a lightweight single-run guard across multiple app instances without adopting Quartz.
- Do not use this skill when the scheduled work must survive restarts, persist triggers in JDBC, or coordinate Quartz-specific job state.

## Common-Case Workflow

1. Start from the job contract: what runs, how often, and what happens on failure or overlap.
2. Use `@Scheduled` for simple app-local recurring work.
3. Use `TaskScheduler` or `SchedulingConfigurer` when trigger registration must be programmatic or scheduler configuration must be explicit.
4. Keep task handles when runtime cancellation or re-registration is required.
5. Add ShedLock when the same `@Scheduled` job must run on only one node across multiple app instances.
6. Escalate to Quartz only when persistence, clustering, or Quartz-specific trigger behavior is truly required.

## Minimal Setup

Enable scheduling once in the application. In Spring Boot, `@EnableScheduling` still turns on annotation-driven scheduling, while Boot supplies the default scheduler bean with pool size `1` unless you override it.

```java
@Configuration
@EnableScheduling
class SchedulingConfiguration {
}
```

## First Runnable Commands or Code Shape

Start with one explicit fixed-delay job:

```java
@Component
class SessionCleanupJob {

    @Scheduled(fixedDelay = 5_000, initialDelay = 30_000)
    void cleanExpiredSessions() {
        // cleanup work
    }
}
```

---

*Applies when:* one JVM instance can own the recurring work and the first execution should wait until startup settles.

## Ready-to-Adapt Templates

Fixed-delay task:

```java
@Component
class SessionCleanupJob {

    @Scheduled(fixedDelay = 5_000, initialDelay = 30_000)
    void cleanExpiredSessions() {
        // cleanup work
    }
}
```

---

*Applies when:* overlap should be naturally avoided by finishing one run before the next delay starts, and the first run should not race startup.

Fixed-rate task:

```java
@Component
class MetricsPushJob {

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    void pushMetrics() {
        // publish metrics
    }
}
```

---

*Applies when:* cadence matters more than completion-to-next-start delay and the work is safe for the scheduler model you chose.

Cron task:

```java
@Component
class BillingSummaryJob {

    @Scheduled(cron = "0 0 6 * * MON-FRI", zone = "UTC")
    void sendSummary() {
        // send summary
    }
}
```

---

*Applies when:* the schedule is business-calendar oriented instead of interval oriented.

Important cron note:

- `initialDelay` applies to fixed-delay and fixed-rate scheduling, not cron-based scheduling

Spring Boot scheduler properties:

```yaml
spring:
  task:
    scheduling:
      pool:
        size: 4
      thread-name-prefix: jobs-
```

---

*Applies when:* several scheduled tasks exist and pool sizing or thread naming matters operationally, or Boot's default single scheduler thread is no longer enough.

Dynamic registration with `SchedulingConfigurer`:

```java
@Configuration
@EnableScheduling
class DynamicSchedulingConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addCronTask(() -> runCleanup(), "0 */15 * * * *");
    }

    void runCleanup() {
        // cleanup work
    }
}
```

---

*Applies when:* trigger registration depends on configuration or a more explicit scheduler setup.

Dynamic registration and cancellation with `ScheduledFuture`:

```java
@Service
class DynamicTaskService {
    private final TaskScheduler taskScheduler;
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    DynamicTaskService(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    void registerFixedRate(String id, Runnable task, Duration period) {
        cancel(id);
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(task, period);
        tasks.put(id, future);
    }

    boolean cancel(String id) {
        ScheduledFuture<?> future = tasks.get(id);
        if (future == null) {
            return false;
        }
        boolean cancelled = future.cancel(false);
        if (cancelled) {
            tasks.remove(id, future);
        }
        return cancelled;
    }

    Set<String> listTaskIds() {
        return Set.copyOf(tasks.keySet());
    }
}
```

---

*Applies when:* tasks are created dynamically and must be cancelled or replaced at runtime without Quartz.

Programmatic task inventory:

```java
@Component
class ScheduledTaskMonitor {
    private final ScheduledTaskHolder scheduledTaskHolder;

    ScheduledTaskMonitor(ScheduledTaskHolder scheduledTaskHolder) {
        this.scheduledTaskHolder = scheduledTaskHolder;
    }

    Set<ScheduledTask> tasks() {
        return scheduledTaskHolder.getScheduledTasks();
    }
}
```

---

*Applies when:* the application needs to list known Spring-managed scheduled tasks or feed a lightweight monitoring surface.

Observed task execution wrapper:

```java
@Service
class ObservedTaskService {
    private static final Logger log = LoggerFactory.getLogger(ObservedTaskService.class);

    private final MeterRegistry meterRegistry;

    ObservedTaskService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    Runnable observed(String taskName, Runnable delegate) {
        return () -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            log.info("task.start name={}", taskName);
            try {
                delegate.run();
                Counter.builder("app.scheduling.task.success")
                        .tag("task", taskName)
                        .register(meterRegistry)
                        .increment();
                log.info("task.success name={}", taskName);
            } catch (RuntimeException ex) {
                Counter.builder("app.scheduling.task.failure")
                        .tag("task", taskName)
                        .register(meterRegistry)
                        .increment();
                log.error("task.failure name={}", taskName, ex);
                throw ex;
            } finally {
                sample.stop(Timer.builder("app.scheduling.task.duration")
                        .tag("task", taskName)
                        .register(meterRegistry));
            }
        };
    }
}
```

---

*Applies when:* operations need visibility into task starts, failures, and duration rather than silent background execution.

## Validate the Result

Validate the common case with these checks:

- `@EnableScheduling` is actually enabled once in the app
- the chosen mode matches the intent: fixed delay vs fixed rate vs cron
- `initialDelay` is used deliberately for warmup-style delayed starts rather than copied onto cron jobs
- overlap and failure behavior are explicitly considered rather than assumed away
- time zone is explicit for cron-based business schedules
- thread-pool sizing is explicit when several jobs may run concurrently
- dynamic tasks keep their `ScheduledFuture` handles somewhere intentional so they can be cancelled or replaced
- task inventory is observable when operations need to know what is scheduled locally
- scheduled work emits enough logging and metrics to diagnose silent failure or drift
- multi-instance deployments use a lock strategy such as ShedLock when only one node should run the task

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| choosing between fixed delay, fixed rate, cron, and task-registration models | `./references/scheduling-patterns.md` |
| configuring `TaskScheduler`, `SchedulingConfigurer`, or advanced trigger wiring | `./references/scheduler-config-recipes.md` |
| dynamic registration, cancellation, and local monitoring patterns | `./references/runtime-task-control.md` |
| logging, metrics, and observability patterns for scheduled tasks | `./references/scheduling-observability.md` |

## Invariants

- MUST define the job contract before picking a scheduling mode.
- SHOULD use `@Scheduled` for simple app-local recurring work.
- MUST make overlap assumptions explicit.
- SHOULD keep cron jobs time-zone explicit.
- MUST treat scheduler pool sizing as an operational choice, not an afterthought.
- MUST keep `ScheduledFuture` handles when runtime cancellation or replacement is required.
- MUST choose a distributed lock or different scheduler when one-active-node semantics are required across instances.
- SHOULD make scheduled work observable through logs and task-level metrics.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| using cron when a fixed delay is the real requirement | the schedule becomes harder to reason about than necessary | use the smallest scheduling mode that fits the job |
| assuming scheduled methods never overlap | repeated schedules can still overlap depending on pool and trigger shape | make concurrency and pool behavior explicit |
| scaling `@Scheduled` to multiple app instances with no lock strategy | every instance can run the same task at once | add ShedLock or move to a scheduler that coordinates cluster-wide execution |
| omitting the cron time zone | production timing drifts across environments | set `zone` for business-calendar schedules |
| dynamically registering tasks without storing their handles | the application cannot cancel or replace them cleanly | keep `ScheduledFuture` handles in an explicit registry |
| running scheduled work silently with no logging or metrics | failures and drift become hard to diagnose | emit task identity, outcome, and duration signals |
| treating app-local scheduling like durable job orchestration | restart survival and cluster behavior are not guaranteed | escalate to Quartz when persistence or cluster-wide control is required |

## Scope Boundaries

- Activate this skill for:
  - `@Scheduled` jobs
  - `TaskScheduler` configuration
  - lightweight app-local recurring work in Spring
- Do not use this skill as the primary source for:
  - durable Quartz jobs requiring restart survival, JDBC-backed triggers, or cluster-grade scheduling
  - Spring Batch job modeling
  - generic asynchronous message-driven orchestration
