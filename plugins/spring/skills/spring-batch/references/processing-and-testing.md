# Processing and testing

## Reader, processor, writer composition

The ordinary item-oriented path is reader + optional processor + writer.

- Keep the reader deterministic for the same job parameters.
- Treat the processor as optional.
  Omit it when the writer can safely consume the reader output directly.
- Keep the writer idempotent or restart-safe when restartability matters.
- Add an `ItemProcessor` only when processing logic is a concrete requirement.
- Open the reader and writer reference for reader, writer, and `ItemStream` implementation details.

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
- Use skip for bad input that should be recorded and bypassed.
- Use retry for transient failures that may succeed on a later attempt.

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

Open the scaling reference when throughput or distribution requirements need a different execution model.
Use measurements and restart evidence to justify scaling an existing job.

## Minimal testing posture

Start with unit tests for processors, policies, and transition logic.
Use a Spring Batch integration test only when job repository state, step wiring, restart behavior, or the real job runtime defines the behavior.
Reserve an end-to-end job journey for a distinct production-critical flow that lower-level tests do not prove.
Choose tests for acceptance criteria and regression risks, not a fixed ratio of test layers.
Review prose guidance directly instead of testing its wording or file layout.

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

- For changed job behavior, verify the intended exit status with representative parameters.
- Cover affected restart, skip, retry, or late-binding behavior when it defines an acceptance criterion or regression risk.
- Verify changed restart-sensitive steps do not duplicate already committed work.
