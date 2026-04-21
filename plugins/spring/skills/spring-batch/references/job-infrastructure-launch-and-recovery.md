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
JobExecution execution = jobOperator.startNextInstance(importJob);
```

## Restart versus rerun versus recover blocker

Choose the operator action deliberately:

- restart: same logical instance, next execution attempt
- rerun: new logical instance with different identifying parameters
- recover: mark a stuck `STARTED` execution as restartable on a Batch 6 path before a later restart attempt

```java
JobExecution recoveredExecution = jobOperator.recover(jobExplorer.getJobExecution(failedExecutionId));
JobExecution restartedExecution = jobOperator.restart(recoveredExecution);
```

Use recovery only when reader, writer, and execution-context state are compatible with continued processing. Recovery does not resume work by itself; it makes the stuck execution restartable so a later `restart(...)` can continue the logical instance. On the stable Spring Boot 3.4.x path with Spring Batch 5.2.x, prefer restart and rerun guidance unless the application has intentionally moved to a Batch 6 line that exposes recovery support.

## Repository choices

Default to the framework-managed persistent repository. Treat repository selection as an operational decision.

```java
@Configuration
@EnableBatchProcessing
class BatchInfrastructureConfiguration {
}
```

Use a Mongo-backed repository only when the platform already standardizes on Mongo for operational metadata and the Batch 6 repository model fits the deployment.

Stable Spring Boot 3.4.x still manages Spring Batch 5.2.x. Open the Batch 6 migration reference before copying 6.x-specific repository annotations or operator behavior into a Boot-managed application.

## Graceful shutdown blocker

When the deployment must stop safely mid-run, make stop semantics explicit and prefer steps that can persist and resume state cleanly.

```java
jobOperator.stop(execution);
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
