# Spring Batch fault tolerance and transaction tuning

Open this reference when the task involves transaction attributes, skip or retry rules, async item pipelines, or framework-level repeat and retry decisions beyond the common path in [SKILL.md](../SKILL.md).

## Transaction and commit blocker

Tune commit interval and transaction attributes to the workload instead of relying on copied defaults.

```java
new StepBuilder("importStep", repository).<CustomerInput, Customer>chunk(100, tx).transactionAttribute(txAttributes).build();
```

Commit size, isolation, timeout, and retry behavior must agree with the writer and target system.

## Skip and retry blocker

Use skip for items that may be discarded safely. Use retry for transient failures that should succeed on another attempt.

```java
new StepBuilder("importStep", repository).<CustomerInput, Customer>chunk(100, tx).faultTolerant().skipLimit(10).skip(FlatFileParseException.class).retryLimit(3).retry(DeadlockLoserDataAccessException.class).build();
```

Do not use retry for validation failures that will never succeed on another attempt.

## Async item pipeline blocker

Use async item processing only when ordering and retry semantics are still acceptable.

```java
AsyncItemProcessor<CustomerInput, Customer> processor = new AsyncItemProcessor<>();
AsyncItemWriter<Customer> writer = new AsyncItemWriter<>();
```

Async item pipelines complicate error handling and restart reasoning. Prove that simpler synchronous chunks are not enough first.

## Repeat and retry internals

- `RepeatTemplate` belongs here when the loop itself is custom.
- Framework-level retry decisions should still map cleanly to business failure categories.
- Keep custom repeat logic out of ordinary chunk jobs unless the simpler step DSL cannot express the requirement.

## Decision points

| Situation | First check |
| --- | --- |
| Failures should be tolerated | classify skip versus retry explicitly |
| A step stops badly under shutdown | verify stop-capable step and transaction boundaries |
| Async processing is proposed | verify ordering, retry, and restart implications |
| Commit behavior is unstable | verify chunk size, transaction attributes, and writer semantics together |
