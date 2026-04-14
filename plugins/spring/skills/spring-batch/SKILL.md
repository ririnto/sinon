---
name: spring-batch
description: >-
  This skill should be used when the user asks to "design a Spring Batch job", "configure job steps", "use chunk processing", "scale a batch workload", or needs guidance on Spring Batch jobs, steps, readers, writers, and retries.
---

# Spring Batch

## Overview

Use this skill to design Spring Batch jobs, steps, chunk-oriented processing, retry and skip behavior, and scaling paths. The common case is one job, one or more explicit steps, and one deliberate fault-tolerance policy. Focus on job flow and restart semantics before tuning readers or infrastructure.

## Use This Skill When

- You are designing Spring Batch jobs or steps.
- You need to choose chunk processing versus tasklets.
- You need retry, skip, restart, or scaling guidance for batch work.
- Do not use this skill when the problem is ordinary request/response API flow rather than batch processing.

## Common-Case Workflow

1. Define the batch job outcome first.
2. Break the job into explicit steps.
3. Choose chunk-oriented processing for record streams and tasklets for one-off procedural work.
4. Add retry, skip, or scaling only where the workload and failure shape justify it.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-batch</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one chunk-oriented step and one job:

```java
@Bean
Step importOrdersStep(JobRepository jobRepository,
                      PlatformTransactionManager transactionManager,
                      ItemReader<OrderRow> reader,
                      ItemWriter<OrderRow> writer) {
    return new StepBuilder("importOrders", jobRepository)
            .<OrderRow, OrderRow>chunk(100, transactionManager)
            .reader(reader)
            .writer(writer)
            .build();
}

@Bean
Job importOrdersJob(JobRepository jobRepository, Step importOrdersStep) {
    return new JobBuilder("importOrdersJob", jobRepository)
            .start(importOrdersStep)
            .build();
}
```

---

*Applies when:* you need the default Spring Batch shape before adding retry, skip, or scaling.

## Ready-to-Adapt Templates

Chunk-oriented step:

```java
@Bean
Step importOrdersStep(JobRepository jobRepository,
                      PlatformTransactionManager transactionManager,
                      ItemReader<OrderRow> reader,
                      ItemWriter<OrderRow> writer) {
    return new StepBuilder("importOrders", jobRepository)
            .<OrderRow, OrderRow>chunk(100, transactionManager)
            .reader(reader)
            .writer(writer)
            .build();
}
```

---

*Applies when:* the job processes a sequence of records rather than one procedural task.

Fault-tolerant step:

```java
.faultTolerant()
.retry(TransientDataAccessException.class)
.retryLimit(3)
.skip(InvalidOrderException.class)
.skipLimit(10)
```

---

*Applies when:* the failure taxonomy is already understood and not all failures are equal.

Tasklet step:

```java
@Bean
Step cleanupStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
    return new StepBuilder("cleanupStep", jobRepository)
            .tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED, transactionManager)
            .build();
}
```

---

*Applies when:* the batch work is procedural rather than item-stream oriented.

## Validate the Result

Validate the common case with these checks:

- the job outcome and step boundaries are explicit
- chunk processing is used only for record-stream workloads
- retry and skip rules reflect a real failure taxonomy
- scaling is not introduced until the single-node flow is already correct and restart semantics are understood

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| chunk vs tasklet and batch decomposition heuristics | `./references/batch-patterns.md` |
| `JobBuilder`, `StepBuilder`, retry, skip, and scaling recipes | `./references/batch-builders.md` |

## Invariants

- MUST model job outcome and step boundaries explicitly.
- SHOULD use chunk processing for record streams.
- MUST add retry and skip based on failure shape, not by default.
- SHOULD scale only after local job flow is clear.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| making one giant step instead of modeling real job boundaries | restart, fault handling, and reasoning all get worse | break the job into explicit step units |
| adding partitioning before the single-threaded flow is understood | distributed complexity hides basic correctness issues | prove the local flow first |
| using retry and skip without a failure taxonomy | records and failures are handled inconsistently | classify failures before configuring fault tolerance |

## Scope Boundaries

- Activate this skill for:
  - Spring Batch job and step structure
  - chunk processing and fault tolerance
  - scaling batch workloads
- Do not use this skill as the primary source for:
  - ordinary request/response APIs
  - generic message-flow orchestration
  - Kafka messaging design
