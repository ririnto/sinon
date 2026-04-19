# Spring Batch job infrastructure, launch, and recovery

Open this reference when the task is about `JobRepository`, `JobLauncher`, `JobOperator`, metadata storage, restart versus rerun semantics, recovery, or graceful shutdown.

## Parameter identity blocker

Use identifying parameters only for values that define the logical job instance. Keep operational knobs non-identifying.

```java
JobParameters parameters = new JobParametersBuilder().addString("input", "classpath:/customers.csv").addLong("requestedAt", System.currentTimeMillis(), false).toJobParameters();
```

If `requestedAt` is identifying, every launch becomes a new instance and restart semantics change.

## Launch blocker

Use `JobLauncher` for application-driven starts and `JobOperator` for operational actions.

```java
JobExecution execution = jobLauncher.run(importJob, parameters);
```

```java
long executionId = jobOperator.startNextInstance("importJob");
```

## Restart versus rerun versus recover blocker

Choose the operator action deliberately:

- restart: same logical instance, next execution attempt
- rerun: new logical instance with different identifying parameters
- recover: continue from a failed execution state when supported by the operator path

```java
long recoveredExecutionId = jobOperator.recover(failedExecutionId);
```

Use recovery only when reader, writer, and execution-context state are compatible with continued processing.

## Repository choices

Default to the framework-managed persistent repository. Treat repository selection as an operational decision.

```java
@Configuration
@EnableBatchProcessing
class BatchInfrastructureConfiguration {
}
```

Use a Mongo-backed repository only when the platform already standardizes on Mongo for operational metadata and the Batch 6 repository model fits the deployment.

## Graceful shutdown blocker

When the deployment must stop safely mid-run, make stop semantics explicit and prefer steps that can persist and resume state cleanly.

```java
jobOperator.stop(executionId);
```

Pair stop behavior with restart tests before production rollout.

## Metadata and operations checklist

- Keep metadata tables or collections backed up.
- Index hot lookup paths for job and step execution queries.
- Archive old metadata before operator queries become slow.
- Make exit status and failure exception visibility part of operational support.

## Decision points

| Situation | First check |
| --- | --- |
| A rerun unexpectedly starts a new instance | verify which parameters are identifying |
| A restart duplicates committed work | verify reader and writer restart state plus execution context |
| Operators cannot stop or recover safely | verify `JobOperator` path and repository state |
| Metadata queries become slow | verify repository indexing and archival strategy |
