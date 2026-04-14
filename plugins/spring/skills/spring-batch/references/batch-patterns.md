---
title: Spring Batch Patterns Reference
description: >-
  Reference for Spring Batch job shape, chunk versus tasklet choices, and batch patterns.
---

Use this reference when you need to decide how a job should be decomposed before touching builders.

## Fault-Tolerance Rule

- retry transient infrastructure failures
- skip only intentionally disposable records
- fail fast when data correctness is the real concern

## Chunk vs Tasklet Decision Aid

| Use chunk processing when... | Use tasklet processing when... |
| --- | --- |
| input is a stream of records with natural item boundaries | work is not naturally itemized (one file, one API call, one bulk operation) |
| you need per-chunk commit granularity | you need one commit after the entire step completes |
| skip and retry per item are meaningful | the operation is all-or-nothing |
| the processing is read-transform-write oriented | the step is a single procedure (e.g., call external service, emit one report) |

```java
// Chunk-oriented step
@Bean
Step chunkStep(JobRepository jobRepository,
               PlatformTransactionManager txManager,
               ItemReader<Trade> reader,
               ItemProcessor<Trade, TradeRecord> processor,
               ItemWriter<TradeRecord> writer) {
    return new StepBuilder("chunkStep", jobRepository)
            .<Trade, TradeRecord>chunk(100, txManager)  // commit every 100 items
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .faultTolerant()
            .retry(TransientDataAccessException.class).retryLimit(3)
            .skip(InvalidTradeRecord.class).skipLimit(50)
            .build();
}

// Tasklet-oriented step
@Bean
Step taskletStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
    return new StepBuilder("taskletStep", jobRepository)
            .tasklet((contribution, ctx) -> {
                // single transactional boundary for the entire step
                fileArchiver.archive(ctx);
                return RepeatStatus.FINISHED;
            }, txManager)
            .build();
}
```

## Transaction Rule

State the transaction boundary at the step level instead of assuming the default chunk behavior is obvious.

- chunk steps commit per chunk
- tasklets need explicit transactional intent because they are procedural rather than record-stream oriented
- idempotent writers matter when restart behavior may replay work

## Restartability and Idempotent Writer Rule

Design restart semantics before relying on fault tolerance.

- item readers and writers should tolerate re-entry
- partitioning keys should be deterministic
- job parameters must identify the logical batch run clearly

### Idempotent writer pattern

Writers must tolerate re-execution when a job restarts from a checkpoint:

```java
@Component
public class IdempotentTradeWriter implements ItemWriter<TradeRecord> {

    @Autowired
    private TradeRepository repository;

    @Override
    public void write(Chunk<? extends TradeRecord> chunk) throws Exception {
        for (TradeRecord record : chunk) {
            // upsert rather than blind insert
            repository.upsert(record.getTradeId(), record.getPayload());
            // upsert implementation: UPDATE if exists, INSERT if not
            // this handles restart replays without duplicate or PK violation
        }
    }
}
```

Do not assume a failed chunk can simply be re-run. If the writer is not idempotent, a restart will produce duplicates or secondary-key violations.

`JobBuilder`, `StepBuilder`, and fault-tolerant builder recipes belong in the builder-focused batch guidance used after the job shape is already settled.
