# Spring Batch testing, step scope, and failure paths

Open this reference when the ordinary end-to-end job test in [SKILL.md](../SKILL.md) is not enough and the task needs step tests, scoped component tests, failure-path assertions, restart tests, or metadata-driven test setup.

## Step test blocker

Use step-launch tests when the job is too large for every test to run end to end.

```java
jobOperatorTestUtils.setJob(importJob);
JobExecution execution = jobOperatorTestUtils.startStep("importStep", new JobParametersBuilder().addString("input", "classpath:/customers.csv").toJobParameters(), new ExecutionContext());
```

```java
assertAll(
    () -> assertEquals(BatchStatus.COMPLETED, execution.getStatus()),
    () -> assertEquals(100, execution.getStepExecutions().iterator().next().getWriteCount())
);
```

## Scope test blocker

Use scoped test utilities when `@StepScope` or `@JobScope` components depend on parameters or execution context.

```java
StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution(new JobParametersBuilder().addString("input", "classpath:/customers.csv").toJobParameters());
```

Bind the scope explicitly before asserting reader or writer behavior.

```java
int count = StepScopeTestUtils.doInStepScope(stepExecution, () -> customerReader.read() != null ? 1 : 0);
assertEquals(1, count);
```

## Restart and failure blocker

Do not stop at the happy path. Add one restart, skip, retry, or failure classification test.

```java
assertEquals(BatchStatus.FAILED, execution.getStatus());
```

```java
jobOperatorTestUtils.setJob(importJob);
JobExecution failed = jobOperatorTestUtils.startJob(new JobParametersBuilder().addString("input", "classpath:/customers.csv").toJobParameters());
JobExecution restarted = jobOperator.restart(failed);
assertEquals(BatchStatus.COMPLETED, restarted.getStatus());
```

## Metadata-driven assertions

Assert on `ExitStatus`, `BatchStatus`, read counts, write counts, skip counts, and execution context changes when those are part of the contract.

## Verification rule

Verify one scoped component test resolves late-bound parameters and one restart test proves already committed work is not written twice.

## Decision points

| Situation | First check |
| --- | --- |
| The whole job is too heavy for every test | launch a step instead |
| A scoped bean fails in tests | create metadata and bind scope explicitly |
| Restart behavior is critical | add a failed execution and rerun assertion |
| Failure handling is custom | assert counts, status, and listener effects explicitly |
