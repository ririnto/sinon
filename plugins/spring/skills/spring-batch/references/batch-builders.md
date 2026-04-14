---
title: Spring Batch Builder Recipes
description: >-
  Reference for Spring Batch job, step, and fault-tolerant builder recipes.
---

Use this reference when the job shape is known and the remaining work is wiring builders correctly.

## Job and Step Builder Shapes

These shapes are additive to the canonical templates in `SKILL.md`; they show the builder call order and the most common configuration points.

### Simple job with one chunk step

```java
@Bean
Job importJob(JobRepository jobRepository, Step step1) {
    return new JobBuilder("importJob", jobRepository)
            .start(step1)
            .build();
}

@Bean
Step step1(JobRepository jobRepository,
           PlatformTransactionManager txManager,
           ItemReader<Trade> reader,
           ItemProcessor<Trade, TradeRecord> processor,
           ItemWriter<TradeRecord> writer) {
    return new StepBuilder("step1", jobRepository)
            .<Trade, TradeRecord>chunk(100, txManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
}
```

### Job with two sequential steps and a decision

```java
@Bean
Job multiStepJob(JobRepository jobRepository,
                 Step loadStep,
                 Step processStep,
                 Step cleanupStep,
                 JobExecutionDecider decider) {
    return new JobBuilder("multiStepJob", jobRepository)
            .start(loadStep)
            .next(decider)  // decide based on LoadResult
            .on("COMPLETED").to(processStep)
            .from(decider)
            .on("SKIPPED").to(cleanupStep)
            .end()
            .build();
}
```

### Partitioned step

```java
@Bean
Step workerStep(JobRepository jobRepository,
                PlatformTransactionManager txManager,
                ItemReader<Record> reader,
                ItemProcessor<Record, Result> processor,
                ItemWriter<Result> writer) {
    return new StepBuilder("workerStep", jobRepository)
            .<Record, Result>chunk(50, txManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
}

@Bean
Step partitionedStep(JobRepository jobRepository,
                     Step workerStep,
                     Partitioner partitioner,
                     TaskExecutor taskExecutor) {
    return new StepBuilder("partitionedStep", jobRepository)
            .partitioner(workerStep.getName(), partitioner)
            .step(workerStep)
            .taskExecutor(taskExecutor)
            .gridSize(4)
            .build();
}

@Bean
Partitioner partitioner() {
    return gridSize -> {
        Map<String, ExecutionContext> partitions = new HashMap<>();
        for (int i = 0; i < gridSize; i++) {
            ExecutionContext ctx = new ExecutionContext();
            ctx.putInt("partition", i);
            partitions.put("partition_" + i, ctx);
        }
        return partitions;
    };
}
```

## Fault-Tolerant Recipe

```java
.faultTolerant()
.retry(TransientDataAccessException.class)
.retryLimit(3)
.skip(InvalidOrderException.class)
.skipLimit(10)
```

Use retry for transient infrastructure failures.
Use skip only when invalid records can be intentionally discarded.

## Scaling Rule

Move to partitioning or remote chunking only after:

- single-threaded flow is correct
- item boundaries are stable
- restart and idempotency behavior are understood
