---
name: "spring-batch"
description: "Build and operate Spring Batch jobs with job/step configuration, chunk or tasklet processing, restartability, reader or writer choices, and scaling patterns. Use this skill when building or operating Spring Batch jobs with job and step configuration, chunk or tasklet processing, job parameters, restartability, reader or writer choices, scaling patterns, and batch-focused tests."
metadata:
  title: "Spring Batch"
  official_project_url: "https://spring.io/projects/spring-batch"
  reference_doc_urls:
    - "https://docs.spring.io/spring-batch/reference/index.html"
  version: "6.0.x"
---

Use this skill when building or operating Spring Batch jobs with job and step configuration, chunk or tasklet processing, job parameters, restartability, reader or writer choices, scaling patterns, and batch-focused tests.

## Boundaries

Use `spring-batch` for scheduled or launched batch jobs, chunk and tasklet steps, restart semantics, metadata-backed execution, and large-scale record processing.

- Use `spring-integration` for general message-driven integration flows rather than batch job orchestration.
- Use `spring-data` when the main task is repository design rather than batch orchestration.
- Keep domain logic in readers, processors, writers, tasklets, or delegated services. Batch configuration should orchestrate steps, not embed the business model.

## Common path

The ordinary Spring Batch job is:

1. Decide the job name, step names, metadata repository, and parameter identity before writing business code.
2. Start with one linear chunk step unless the work is naturally one-shot and better expressed as a tasklet.
3. Wire reader, optional processor, and writer with restart assumptions explicit.
4. Add late binding with `@StepScope` or `@JobScope` only when parameters or execution context must resolve at runtime.
5. Add skip, retry, listener, and transaction tuning only for concrete failure modes.
6. Prove the happy path and one restart or failure path in tests before adding scaling or remote execution.

## Dependency baseline

Use the Boot starter for application code and the Batch test module for job and step tests.

For the latest released line, Spring Batch itself is 6.0.3. The stable Spring Boot 3.4.x line still manages Spring Batch 5.2.x, so Batch 6-specific APIs require either a direct Spring Batch 6.x path or a Spring Boot line that has moved to Batch 6.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-batch</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.batch</groupId>
        <artifactId>spring-batch-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Treat the Boot-managed path and the standalone current-line path as different compatibility branches. Verify the actual Batch line before copying infrastructure or migration examples that depend on Batch 6 behavior.

## Core batch terms

Keep the batch runtime vocabulary explicit:

| Type | Role |
| --- | --- |
| `JobInstance` | one logical run identity defined by the identifying job parameters |
| `JobExecution` | one concrete execution attempt of a job instance |
| `JobParameters` | launch parameters that define identity or operational knobs |
| `ExecutionContext` | restart state persisted across step or job execution |
| `JobRepository` | metadata store for instances, executions, and step state |
| `JobOperator` | operational control surface for launch, stop, restart, and recovery |

## Minimal job model

The minimum Spring Batch model is `Job -> Step -> chunk or tasklet`.

- Use a **chunk step** for item-oriented work: read, optionally process, then write in transactions.
- Use a **tasklet step** for inherently one-shot work such as cleanup, archive, trigger, or command execution.
- Keep one linear job path until a real branching or scaling need exists.

## First safe configuration

### Infrastructure shape

On Spring Boot's Batch 5.2.x path (current Boot 3.x), `@EnableBatchProcessing` alone provides the framework-managed `JobRepository` and transaction manager backed by the Boot `DataSource`:

```java
@Configuration
@EnableBatchProcessing
class BatchInfrastructureConfiguration {
}
```

On Spring Batch 6, `@EnableBatchProcessing` no longer assumes a JDBC-backed store. It configures the common batch infrastructure and defaults to a `ResourcelessJobRepository` plus `ResourcelessTransactionManager` (in-memory, non-persistent). Opt into a persistent backend explicitly with one of the store-specific annotations:

```java
@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository
class BatchInfrastructureConfiguration {
}
```

```java
@Configuration
@EnableBatchProcessing
@EnableMongoJobRepository
class BatchInfrastructureConfiguration {
}
```

`@EnableJdbcJobRepository` and `@EnableMongoJobRepository` attributes (`dataSourceRef`, `transactionManagerRef`, `tablePrefix`, etc.) are all optional; add them only when overriding the defaults. Change repository strategy only when operations, scale, or platform constraints require it. Open the infrastructure reference before adopting Batch 6-specific migration behavior beyond these annotations.

### Chunk job baseline

```java
@Configuration
class ImportJobConfiguration {
    @Bean
    Job importJob(JobRepository repository, Step importStep) {
        return new JobBuilder("importJob", repository)
            .start(importStep)
            .build();
    }

    @Bean
    Step importStep(JobRepository repository, PlatformTransactionManager tx, ItemReader<CustomerInput> reader, ItemProcessor<CustomerInput, Customer> processor, ItemWriter<Customer> writer) {
        return new StepBuilder("importStep", repository)
            .<CustomerInput, Customer>chunk(100, tx)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }
}
```

## Reader, processor, writer composition

The ordinary item-oriented path is reader + optional processor + writer.

- Keep the reader deterministic for the same job parameters.
- Treat the processor as optional; omit it when the writer can safely consume the reader output directly.
- Keep the writer idempotent or restart-safe when restartability matters.
- Open the reader and writer reference when processing logic itself becomes the blocker and needs a dedicated `ItemProcessor` seam.

Use late binding only when runtime parameters or execution context must resolve at step creation time.

```java
@Bean
@StepScope
FlatFileItemReader<CustomerInput> customerReader(@Value("#{jobParameters['input']}") Resource input) {
    return new FlatFileItemReaderBuilder<CustomerInput>()
        .name("customerReader")
        .resource(input)
        .delimited()
        .names("email", "name")
        .targetType(CustomerInput.class)
        .build();
}
```

## Restartability and fault-tolerance basics

Make restart behavior explicit before tuning performance.

- Decide which job parameters identify a `JobInstance` and which are only operational knobs.
- Keep restart-sensitive state in `ExecutionContext` or restart-safe `ItemStream` implementations.
- Use **skip** for bad input that should be recorded and bypassed.
- Use **retry** for transient failures that may succeed on a later attempt.

```java
.faultTolerant()
.skipLimit(10)
.skip(FlatFileParseException.class)
.retryLimit(3)
.retry(DeadlockLoserDataAccessException.class)
```

## Scaling decision ladder

Choose the smallest scaling model that solves throughput before reaching for remote patterns.

| Need | Start here |
| --- | --- |
| one step is sufficient | single-threaded chunk or tasklet step |
| one job has independent branches | parallel flows |
| one step needs more local throughput | multithreaded step |
| input can be split into isolated slices | partitioning |
| work must cross process boundaries | remote chunking or remote step execution |

Open the scaling reference only after the single-step path is correct and measured.

## Minimal testing posture

Start with one end-to-end job test and one restart or failure-path test.

```java
@SpringBatchTest
@SpringJUnitConfig(ImportJobConfiguration.class)
class ImportJobTests {
    @Autowired
    private JobOperatorTestUtils jobOperatorTestUtils;

    @Test
    void jobCompletes(@Autowired Job importJob) throws Exception {
        jobOperatorTestUtils.setJob(importJob);
        JobExecution execution = jobOperatorTestUtils.startJob(new JobParametersBuilder().addString("input", "classpath:/customers.csv").toJobParameters());
        assertEquals("COMPLETED", execution.getExitStatus().getExitCode());
    }
}
```

- Verify the job reaches the intended exit status with representative parameters.
- Verify one representative restart, skip, retry, or late-binding path.
- Verify restart-sensitive steps do not duplicate already committed work.

## Production guardrails

- Keep job names, step names, and parameter semantics stable after schedulers and operators depend on them.
- Keep the metadata repository healthy, backed up, and indexed for the target workload.
- Tune chunk size, retry, and skip rules to real workload and failure patterns.
- Make restart, rerun, stop, and recovery semantics explicit so operators know which action is safe.
- Treat scaling models such as multithreaded steps, partitioning, or remote execution as operational decisions, not default configuration.

## References

- Open [references/job-infrastructure-launch-and-recovery.md](references/job-infrastructure-launch-and-recovery.md) when the task is about `JobRepository`, `JobOperator`, parameter identity, restart versus rerun, metadata, recovery, graceful shutdown, or operational control.
- Open [references/step-flow-and-listeners.md](references/step-flow-and-listeners.md) when the task involves tasklets, listeners, or flow branching.
- Open [references/fault-tolerance-and-transaction-tuning.md](references/fault-tolerance-and-transaction-tuning.md) when the task involves transaction attributes, skip or retry rules, async item pipelines, or framework-level repeat and retry decisions.
- Open [references/readers-writers-and-item-streams.md](references/readers-writers-and-item-streams.md) when the blocker is choosing or implementing a reader, writer, delegate, `ItemStream`, or restart-safe file, database, JSON, XML, or messaging pipeline.
- Open [references/scaling-partitioning-and-remote-execution.md](references/scaling-partitioning-and-remote-execution.md) when one-step throughput is not enough and the task requires multithreaded steps, parallel flows, partitioning, local chunking, remote chunking, or remote step execution.
- Open [references/testing-batch-jobs-and-step-scope.md](references/testing-batch-jobs-and-step-scope.md) when the task needs `spring-batch-test`, scoped component tests, failure-path assertions, restart tests, or metadata-driven test setup.
- Open [references/integration-driven-launch.md](references/integration-driven-launch.md) when the task involves launching jobs through integration channels or feedback messaging.
- Open [references/observability-and-monitoring.md](references/observability-and-monitoring.md) when the task involves Micrometer metrics, JFR, or operator-facing observability work.
- Open [references/spring-batch-6-migration.md](references/spring-batch-6-migration.md) when the blocker is Spring Batch 6 migration-specific behavior or upgraded infrastructure notes.
