---
metadata:
  reference:
    Spring Batch 6:
      version: 6.0.5
      url:
        - https://docs.spring.io/spring-batch/reference/
        - https://docs.spring.io/spring-batch/reference/whatsnew.html
        - https://raw.githubusercontent.com/spring-projects/spring-batch/v6.0.5/spring-batch-core/src/main/java/org/springframework/batch/core/repository/support/ResourcelessJobRepository.java
        - https://repo.maven.apache.org/maven2/org/springframework/batch/spring-batch-bom/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/batch/spring-batch-bom/6.0.5/spring-batch-bom-6.0.5.pom
    Spring Batch 5.2:
      version: 5.2.x
      url:
        - https://docs.spring.io/spring-batch/reference/5.2/
        - https://docs.spring.io/spring-batch/docs/5.2.6/api/org/springframework/batch/core/configuration/annotation/EnableBatchProcessing.html
        - https://repo.maven.apache.org/maven2/org/springframework/batch/spring-batch-core/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/batch/spring-batch-core/5.2.6/spring-batch-core-5.2.6.pom
    Spring Boot 3.4:
      version: 3.4.13
      url:
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.4.13/spring-boot-dependencies-3.4.13.pom
    Spring Boot 3.5:
      version: 3.5.16
      url:
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.16/spring-boot-dependencies-3.5.16.pom
    Spring Boot 4.1:
      version: 4.1.1
      url:
        - https://docs.spring.io/spring-boot/4.1/reference/io/spring-batch.html
        - https://raw.githubusercontent.com/spring-projects/spring-boot/v4.1.1/module/spring-boot-batch/src/main/java/org/springframework/boot/batch/autoconfigure/BatchAutoConfiguration.java
    Spring Boot 4.0:
      version: 4.0.8
      url: https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.0.8/spring-boot-dependencies-4.0.8.pom
name: spring-batch
description: >-
  Use Spring Batch for job design, restartability, readers, writers, retries, and scaling.
---

# Spring Batch

## Boundaries

Use `spring-batch` for jobs, chunk and tasklet steps, restarts, metadata, and large-scale processing.

- General message-driven integration flows are outside this skill's scope unless they launch or coordinate batch jobs.
- Repository design is outside scope unless used by batch readers, writers, or metadata.
- Keep domain logic in readers, processors, writers, tasklets, or delegated services.
  - Batch configuration should orchestrate steps, not embed the business model.

## Task scope

Verify job identity and restart behavior using the same identifying parameters when those contracts change.
For a new job, define names, parameter identity, metadata storage, and restart expectations before choosing its step model.

## Dependency baseline

For new dependencies, check Maven Central for the latest stable versions compatible with the project's Boot and Framework lines; retain existing pins unless authorized to change them.
Use the Boot starter for application code and Batch test support for job and step tests.
Boot 4.0/4.1 manage Batch 6.0; Boot 3.4/3.5 use Batch 5.2.
Batch 6 APIs require Boot 4 or an intentional compatible Batch 6 dependency; Boot 4.1-specific starter and auto-configuration examples require that release line.
Verify the actual Batch line before applying version-specific infrastructure or migration guidance.

## Core batch terms

A `JobInstance` identifies a logical run through identifying parameters; a `JobExecution` is an attempt.
`JobRepository` stores execution metadata and `ExecutionContext` stores restart state.

## Minimal job model

The minimum Spring Batch model is `Job -> Step -> chunk or tasklet`.

- Use a chunk step for item-oriented work: read, optionally process, then write in transactions.
- Use a tasklet step for one-shot work such as cleanup, archive, trigger, or command execution.
- Keep jobs linear until branching or scaling is needed.

## Job configuration

The resourceless repository does not persist metadata or support restarts or concurrent execution; use a persistent repository when needed.
For dependency declarations, version-specific infrastructure, schema setup, or a chunk job example, read [dependencies and job configuration](references/dependencies-and-job-configuration.md).

## Processing, restart, and scaling

Use a deterministic reader, optional processor, and writer; keep the writer idempotent or restart-safe where restartability matters.
Use late binding only when runtime parameters or execution context must resolve at step creation.
Decide which job parameters identify an instance; store restart-sensitive state in `ExecutionContext` or restart-safe `ItemStream` implementations.
Skip bad input that should be recorded and bypassed; retry transient failures that may succeed later.
Choose the smallest scaling model: one step, parallel flows, a multithreaded step, partitioning, or remote execution according to the throughput and distribution need.
Justify scaling an existing job with measurements and restart evidence.
For processing, fault tolerance, scaling, or test examples, read [processing and testing](references/processing-and-testing.md).

## Testing posture

Start with unit tests for processors, policies, and transitions; use integration tests when repository state, wiring, restart behavior, or the runtime defines behavior.
Reserve an end-to-end journey for a distinct production-critical flow lower-level tests cannot prove.
Choose tests for acceptance criteria and regression risks rather than a fixed layer ratio; review prose directly instead of testing its wording or layout.
For changed job behavior, verify exit status with representative parameters; cover restart, skip, retry, or late binding when relevant to acceptance or regression risk.
Verify changed restart-sensitive steps do not duplicate committed work.

## Production guardrails

- Keep job names, step names, and parameter semantics stable after schedulers and operators depend on them.
- Keep the metadata repository healthy, backed up, and indexed for the target workload.
- Tune chunk size, retry, and skip rules to real workload and failure patterns.
- Make restart, rerun, stop, and recovery semantics explicit so operators know which action is safe.
- Treat scaling models such as multithreaded steps, partitioning, or remote execution as operational decisions, not default configuration.

## References

Read the relevant reference for the task:

- Repository, launcher, operator, identity, metadata, recovery, shutdown: [job infrastructure](references/job-infrastructure-launch-and-recovery.md).
- Tasklets, listeners, or branching: [step flow](references/step-flow-and-listeners.md).
- Transaction attributes, skip, retry, or async pipelines: [fault tolerance](references/fault-tolerance-and-transaction-tuning.md).
- Reader, writer, delegate, `ItemStream`, or restart-safe pipeline choice: [readers and writers](references/readers-writers-and-item-streams.md).
- Parallel flows, multithreading, partitioning, or local/remote execution: [scaling](references/scaling-partitioning-and-remote-execution.md).
- Batch test support, scoped tests, failures, restarts, or metadata: [testing](references/testing-batch-jobs-and-step-scope.md).
- Integration-channel job launch or feedback: [integration](references/integration-driven-launch.md).
- Micrometer, JFR, or operator monitoring: [observability](references/observability-and-monitoring.md).
- Batch 6 migration or upgraded infrastructure: [migration](references/6-migration.md).
