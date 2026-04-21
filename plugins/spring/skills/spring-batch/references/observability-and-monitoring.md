# Spring Batch observability and monitoring

Open this reference when the task involves Micrometer metrics, JFR, or operator-facing observability for running batch jobs.

## Micrometer blocker

Emit metrics for job and step counts, durations, failures, and throughput.

```java
Counter completedJobs = meterRegistry.counter("batch.job.completed", "job", "importJob", "result", "COMPLETED");
completedJobs.increment();
```

Use tags that operators actually query, such as job name, step name, and result.

## Listener-backed metrics shape

```java
@Bean
JobExecutionListener metricsListener(MeterRegistry meterRegistry) {
    return new JobExecutionListener() {
        @Override
        public void afterJob(JobExecution jobExecution) {
            Duration duration = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime());
            Timer.builder("batch.job.duration").tag("job", jobExecution.getJobInstance().getJobName()).tag("status", jobExecution.getStatus().name()).register(meterRegistry).record(duration);
        }
    };
}
```

## JFR blocker

Use JFR when the task is production troubleshooting for throughput, lock contention, or unexpected retries.

```bash
jcmd <pid> JFR.start name=batch-debug settings=profile filename=batch-debug.jfr duration=5m
```

- keep JFR tied to concrete performance or reliability investigations
- correlate JFR findings with batch metadata and application logs

## Operator-facing query shape

```java
Gauge.builder("batch.step.write.count", stepExecution, StepExecution::getWriteCount).tag("job", stepExecution.getJobExecution().getJobInstance().getJobName()).tag("step", stepExecution.getStepName()).register(meterRegistry);
```

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

## Verification rule

Verify one failed execution exposes job name, step name, final status, and failure count through the same metrics or logs operators actually inspect.
