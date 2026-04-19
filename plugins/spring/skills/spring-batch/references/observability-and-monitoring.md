# Spring Batch observability and monitoring

Open this reference when the task involves Micrometer metrics, JFR, or operator-facing observability for running batch jobs.

## Micrometer blocker

Emit metrics for job and step counts, durations, failures, and throughput.

```java
meterRegistry.counter("batch.job.completed", "job", "importJob").increment();
```

Use tags that operators actually query, such as job name, step name, and result.

## JFR blocker

Use JFR when the task is production troubleshooting for throughput, lock contention, or unexpected retries.

- keep JFR tied to concrete performance or reliability investigations
- correlate JFR findings with batch metadata and application logs

## Operator-facing observability checklist

- make job and step status visible
- expose failure reasons and retry behavior
- monitor metadata repository health
- watch queue or remote-worker lag for distributed patterns
- keep launch source and parameter identity auditable

## Decision points

| Situation | First check |
| --- | --- |
| Throughput or lock behavior is unclear | use metrics and JFR before changing scaling model |
| Operators lack visibility | expose status, counts, timing, and failure signals first |
