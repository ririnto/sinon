---
title: Quartz Configuration Recipes
description: >-
  Reference for Spring Quartz job, trigger, and scheduler wiring recipes.
---

Use this reference when Quartz is already chosen and the remaining work is job or trigger wiring. Canonical templates for all job and trigger types live in `SKILL.md`; this file holds additive depth only.

## SchedulerFactoryBean Bean Recipe

Use this form when you need a direct bean definition rather than the Boot `SchedulerFactoryBeanCustomizer` approach:

```java
@Bean
SchedulerFactoryBean schedulerFactoryBean(Trigger... triggers) {
    SchedulerFactoryBean factory = new SchedulerFactoryBean();
    factory.setTriggers(triggers);
    return factory;
}
```

Compare with the Boot-idiomatic `SchedulerFactoryBeanCustomizer` approach shown in `SKILL.md`, which integrates more cleanly with auto-configuration.

## Misfire Trigger Configuration Example

Misfire handling controls what happens when a scheduled fire time is missed because the scheduler was down or the previous instance was still running:

```java
@Bean
Trigger dailyTrigger() {
    return TriggerBuilder.newTrigger()
            .forJob("nightlyReportJob")
            .withIdentity("nightly-report")
            .withSchedule(CronScheduleBuilder
                    .cronSchedule("0 0 3 * * ?")
                    .withMisfireHandlingInstructionFireAndProceed())
            // misfireHandlingInstructionFireAndProceed: fire once immediately when scheduler recovers
            // other options: misfireHandlingInstructionIgnoreMisfires, misfireHandlingInstructionDoNothing
            .build();
}
```

For `SimpleTrigger`, use `withMisfireHandlingInstructionFireNow()` or `nextWithExistingCount()` to control recovery behavior.

## Clustered and Persistent Properties Example

For production multi-instance deployments, use a JDBC job store and these properties:

```properties
# application.properties / spring quartz properties
spring.quartz.job-store-type=jdbc
spring.quartz.jdbc.initialize-schema=never  # schema already created by Quartz tables script

spring.quartz.properties.org.quartz.scheduler.instanceName=MyClusteredScheduler
spring.quartz.properties.org.quartz.scheduler.instanceId=AUTO

# critical for clustering: ensures only one node runs a given job at a time
spring.quartz.properties.org.quartz.jobStore.class=org.quartz.impl.jdbcjobstore.JobStoreTX
spring.quartz.properties.org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.StdJDBCDelegate
spring.quartz.properties.org.quartz.jobStore.tablePrefix=QRTZ_
spring.quartz.properties.org.quartz.jobStore.isClustered=true
spring.quartz.properties.org.quartz.jobStore.clusterCheckinInterval=10000

# prevent livelock on long-running jobs
spring.quartz.properties.org.quartz.jobStore.dontSetAutoCommitFalse=false
spring.quartz.properties.org.quartz.jobStore.useProperties=false
```

`isClustered=true` with `JobStoreTX` is the standard pattern for active-passive failover across application instances.
