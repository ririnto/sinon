---
name: spring-quartz
description: >-
  Use this skill when the user asks to "use Quartz with Spring Boot", "configure JobDetail and Trigger", "persist scheduled jobs in JDBC", "cluster scheduled jobs", or needs guidance on Quartz-backed scheduling patterns in Spring.
---

# Spring Quartz

## Overview

Use this skill to build durable scheduled jobs with Quartz in Spring Boot or Spring Framework applications. The common case is one explicit `JobDetail`, one trigger, one `SchedulerFactoryBean` or Boot-managed scheduler, and one decision about whether the schedule must survive restarts. Use Quartz when in-process `@Scheduled` work is not enough because persistence, clustering, trigger lifecycle control, richer runtime operations, or Quartz trigger semantics matter.

## Use This Skill When

- You need durable scheduled jobs that survive restarts.
- You need JDBC-backed trigger or job persistence.
- You need Quartz cron/simple triggers, calendars, or clustered scheduling properties.
- You need to register, pause, resume, delete, or monitor jobs at runtime.
- You need explicit misfire handling, immediate trigger execution, or scheduler standby/start lifecycle control.
- You need to list registered jobs or make scheduler state observable with metrics, health, or logs.
- You need stable job identity and trigger identity even if the scheduler remains in-memory.
- You need richer runtime lifecycle control than `ScheduledFuture` handles alone provide.
- Do not use this skill when a simple app-local `@Scheduled` task is enough.

## Common-Case Workflow

1. Start from the scheduling requirement: durability, clustering, trigger semantics, runtime control, and recovery expectations.
2. Add Quartz only if lightweight Spring scheduling is insufficient, even if the initial scheduler store stays in-memory.
3. Define one `JobDetail` and one trigger before layering extra scheduler properties.
4. Use `Scheduler` APIs directly for runtime registration, deletion, pause/resume, trigger-now flows, and monitoring.
5. Choose in-memory store first unless durable restart behavior or clustering is a real requirement, then move to JDBC-backed state deliberately.
6. Treat misfire behavior and shutdown policy as explicit operational choices, not hidden defaults.

## Minimal Setup

Spring Boot baseline:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-quartz</artifactId>
</dependency>
```

JDBC-backed persistence when durability is required:

```properties
spring.quartz.job-store-type=jdbc
spring.quartz.jdbc.initialize-schema=never
```

Use `initialize-schema=always` only in disposable environments after reviewing the Quartz schema script behavior.

## First Runnable Commands or Code Shape

Start with one durable job and one cron trigger:

```java
@Bean
JobDetailFactoryBean reportJobDetail() {
    JobDetailFactoryBean factory = new JobDetailFactoryBean();
    factory.setJobClass(ReportJob.class);
    factory.setName("reportJob");
    factory.setGroup("reporting");
    factory.setDurability(true);
    return factory;
}

@Bean
CronTriggerFactoryBean reportTrigger(JobDetail reportJobDetail) {
    CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
    factory.setName("reportTrigger");
    factory.setGroup("reporting");
    factory.setJobDetail(reportJobDetail);
    factory.setCronExpression("0 0 6 * * ?");
    return factory;
}
```

---

*Applies when:* a simple `@Scheduled` task is no longer enough because job identity and trigger state matter.

Quartz with in-memory store is still the right fit when:

```text
1. Jobs must be added, replaced, paused, resumed, or listed by stable identity.
2. Operators need to query jobs and triggers from a central scheduler API.
3. One process owns the schedule today, but future persistence or clustering is plausible.
4. Trigger semantics are richer than one annotation on one method.
```

---

*Applies when:* the scheduler can remain in-memory for now, but runtime control and explicit scheduler objects already matter.

## Ready-to-Adapt Templates

Quartz job bean:

```java
public class ReportJob extends QuartzJobBean {

    @Override
    protected void executeInternal(JobExecutionContext context) {
        // run report
    }
}
```

---

*Applies when:* job logic must run under Quartz scheduling rather than annotation-driven scheduling.

Cron trigger:

```java
@Bean
CronTriggerFactoryBean reportTrigger(JobDetail reportJobDetail) {
    CronTriggerFactoryBean factory = new CronTriggerFactoryBean();
    factory.setJobDetail(reportJobDetail);
    factory.setCronExpression("0 0 6 * * ?");
    return factory;
}
```

---

*Applies when:* the schedule is business-calendar oriented and should be managed by Quartz.

Simple trigger:

```java
@Bean
SimpleTriggerFactoryBean syncTrigger(JobDetail syncJobDetail) {
    SimpleTriggerFactoryBean factory = new SimpleTriggerFactoryBean();
    factory.setJobDetail(syncJobDetail);
    factory.setStartDelay(10_000);
    factory.setRepeatInterval(50_000);
    return factory;
}
```

---

*Applies when:* one delayed start and repeat interval are enough.

Boot JDBC job store:

```properties
spring.quartz.job-store-type=jdbc
spring.quartz.overwrite-existing-jobs=true
spring.quartz.wait-for-jobs-to-complete-on-shutdown=true
spring.quartz.properties.org.quartz.scheduler.instanceName=appScheduler
```

---

*Applies when:* jobs must survive restarts and startup or shutdown behavior must be explicit.

Misfire-aware cron trigger:

```java
CronTrigger trigger = TriggerBuilder.newTrigger()
        .withIdentity("reportTrigger", "reporting")
        .forJob(JobKey.jobKey("reportJob", "reporting"))
        .withSchedule(CronScheduleBuilder.cronSchedule("0 0 6 * * ?")
                .withMisfireHandlingInstructionDoNothing())
        .build();
```

---

*Applies when:* the schedule should skip missed windows instead of bursting work after recovery.

Immediate trigger and scheduler lifecycle control:

```java
void triggerNow(String group, String jobName) throws SchedulerException {
    scheduler.triggerJob(JobKey.jobKey(jobName, group));
}

void standbyScheduler() throws SchedulerException {
    scheduler.standby();
}

void startScheduler() throws SchedulerException {
    scheduler.start();
}
```

---

*Applies when:* operators need maintenance-mode or ad hoc execution flows in addition to normal scheduling.

SchedulerFactoryBean customizer:

```java
@Bean
SchedulerFactoryBeanCustomizer quartzCustomizer(Executor executor) {
    return schedulerFactoryBean -> schedulerFactoryBean.setTaskExecutor(executor);
}
```

---

*Applies when:* Quartz needs executor-level customization beyond the auto-configured defaults.

Dynamic job registration:

```java
@Service
class QuartzOperatorService {
    private final Scheduler scheduler;

    QuartzOperatorService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    void registerCron(String group, String name, String cron) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(name, group);
        TriggerKey triggerKey = TriggerKey.triggerKey(name + "Trigger", group);

        JobDetail job = JobBuilder.newJob(ReportJob.class)
                .withIdentity(jobKey)
                .storeDurably()
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                .build();

        if (!scheduler.checkExists(jobKey)) {
            scheduler.addJob(job, false);
        }
        if (scheduler.checkExists(triggerKey)) {
            scheduler.rescheduleJob(triggerKey, trigger);
        } else {
            scheduler.scheduleJob(trigger);
        }
    }
}
```

---

*Applies when:* jobs must be registered or updated after startup rather than only at configuration time.

Cancel or delete a registered job:

```java
boolean cancelTrigger(String group, String triggerName) throws SchedulerException {
    return scheduler.unscheduleJob(TriggerKey.triggerKey(triggerName, group));
}

boolean deleteJob(String group, String jobName) throws SchedulerException {
    return scheduler.deleteJob(JobKey.jobKey(jobName, group));
}
```

---

*Applies when:* operations need to stop future execution or remove the job completely.

Pause and resume a job:

```java
void pauseJob(String group, String jobName) throws SchedulerException {
    scheduler.pauseJob(JobKey.jobKey(jobName, group));
}

void resumeJob(String group, String jobName) throws SchedulerException {
    scheduler.resumeJob(JobKey.jobKey(jobName, group));
}
```

---

*Applies when:* the job should be temporarily stopped and later resumed with the same identity.

Observe registered and executing jobs:

```java
Set<JobKey> listJobs(String group) throws SchedulerException {
    return scheduler.getJobKeys(GroupMatcher.groupEquals(group));
}

List<JobExecutionContext> currentlyExecuting() throws SchedulerException {
    return scheduler.getCurrentlyExecutingJobs();
}
```

---

*Applies when:* operations need runtime visibility rather than just static configuration knowledge.

Observe triggers for a job:

```java
List<? extends Trigger> jobTriggers(String group, String jobName) throws SchedulerException {
    return scheduler.getTriggersOfJob(JobKey.jobKey(jobName, group));
}
```

---

*Applies when:* operators need to see whether a job is registered with the expected trigger set.

Quartz health surface:

```java
@Component
class QuartzHealthIndicator implements HealthIndicator {
    private final Scheduler scheduler;

    QuartzHealthIndicator(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public Health health() {
        try {
            return Health.up()
                    .withDetail("started", scheduler.isStarted())
                    .withDetail("standby", scheduler.isInStandbyMode())
                    .withDetail("shutdown", scheduler.isShutdown())
                    .withDetail("jobsExecutingNow", scheduler.getCurrentlyExecutingJobs().size())
                    .build();
        } catch (SchedulerException ex) {
            return Health.down(ex).build();
        }
    }
}
```

---

*Applies when:* scheduler liveness and activity should be observable from the application itself.

Quartz metrics snapshot:

```java
@Component
class QuartzMetricsBinder {
    QuartzMetricsBinder(Scheduler scheduler, MeterRegistry meterRegistry) throws SchedulerException {
        Gauge.builder("app.quartz.jobs.executing", scheduler, s -> {
            try {
                return s.getCurrentlyExecutingJobs().size();
            } catch (SchedulerException ex) {
                return -1;
            }
        }).register(meterRegistry);

        Gauge.builder("app.quartz.jobs.registered", scheduler, s -> {
            try {
                return s.getJobGroupNames().stream()
                        .mapToInt(group -> {
                            try {
                                return s.getJobKeys(GroupMatcher.groupEquals(group)).size();
                            } catch (SchedulerException ex) {
                                return 0;
                            }
                        })
                        .sum();
            } catch (SchedulerException ex) {
                return -1;
            }
        }).register(meterRegistry);
    }
}
```

---

*Applies when:* the scheduler should be observable in dashboards rather than only through ad hoc API calls.

## Validate the Result

Validate the common case with these checks:

- Quartz is only being used because persistence, trigger identity, runtime lifecycle control, or clustering truly matter
- Quartz is also justified when runtime lifecycle control and scheduler inventory matter even without JDBC storage
- one `JobDetail` maps clearly to one job responsibility
- one trigger expresses the schedule without hidden time assumptions
- JDBC store is enabled only when durable state is actually required
- schema initialization behavior is deliberate and not accidentally destructive
- misfire behavior and shutdown behavior are explicit rather than inherited accidentally
- runtime operations use the correct lifecycle action: unschedule, delete, pause, resume, or reschedule
- running job visibility comes from `Scheduler` inspection rather than assumption
- logs, health, or metrics expose enough scheduler state to support real operations

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| choosing between lightweight Spring scheduling and Quartz | `./references/quartz-vs-scheduling.md` |
| `SchedulerFactoryBean`, `JobDetail`, `CronTrigger`, or `SimpleTrigger` wiring | `./references/quartz-config-recipes.md` |
| JDBC store, overwrite behavior, clustering-style properties, and schema caveats | `./references/quartz-persistence-and-clustering.md` |
| dynamic registration, pause/resume, delete, and monitoring scenarios | `./references/quartz-operations.md` |
| metrics, health, and observability patterns for Quartz runtime state | `./references/quartz-observability.md` |

## Invariants

- MUST justify Quartz with a real durability, trigger, or runtime lifecycle-control requirement.
- SHOULD keep one job responsibility per `JobDetail`.
- MUST make persistence choice explicit.
- MUST make misfire and shutdown behavior explicit when operational recovery matters.
- SHOULD keep trigger semantics visible and reviewable.
- MUST treat schema initialization and overwrite behavior as operationally sensitive choices.
- MUST distinguish unscheduling a trigger from deleting a job definition.
- SHOULD expose runtime scheduler state when operations depend on it.
- SHOULD expose registered and active job state through logs, metrics, or health surfaces.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| adding Quartz for a trivial in-process recurring task | complexity rises without benefit | use plain Spring scheduling first |
| assuming Quartz is only justified by JDBC persistence | richer lifecycle control and stable scheduler identities can matter before persistence does | choose Quartz when operator-grade control is required, even with an in-memory store |
| enabling JDBC schema init without understanding the script behavior | startup can destroy existing scheduler state | review schema initialization behavior before enabling it |
| persisting jobs without defining overwrite policy | startup behavior becomes surprising | set overwrite policy explicitly |
| deleting a job when only one trigger should be stopped | job identity and other triggers may be removed accidentally | unschedule the trigger unless the whole job should disappear |
| assuming Quartz runtime state is obvious without querying it | operators cannot tell what is scheduled or running | use `getJobKeys`, `getTriggersOfJob`, and `getCurrentlyExecutingJobs` |
| keeping Quartz state invisible to dashboards and health checks | runtime issues stay hidden until failures accumulate | publish health and low-cardinality metrics for registered and executing jobs |
| mixing trigger complexity and job logic in one place | schedule semantics become hard to review | keep job logic and trigger wiring separate |

## Scope Boundaries

- Activate this skill for:
  - Quartz jobs and triggers in Spring
  - JDBC-backed scheduling state
  - clustered or durable scheduling requirements
- Do not use this skill as the primary source for:
  - simple app-local `@Scheduled` tasks
  - Spring Batch job design
  - generic asynchronous processing without a scheduling requirement
