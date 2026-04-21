# Spring Batch integration-driven launch

Open this reference when the task involves launching jobs through integration channels or feedback messaging instead of direct application calls.

## Integration blocker

Use integration-driven launch patterns when jobs start from messages, schedules, or externalized orchestration rather than direct application calls.

- launch from a message only when job parameters and idempotency rules are explicit
- publish job completion or failure feedback when downstream systems depend on status
- keep launch transport concerns out of the batch step implementation itself

## Decision points

| Situation | First check |
| --- | --- |
| Jobs start from external events | make parameter identity and idempotency explicit |
| Downstream systems depend on completion state | emit explicit success or failure feedback |

## Inbound launch flow shape

```java
@Configuration
class BatchLaunchFlows {
    @Bean
    IntegrationFlow launchImportJob(JobOperator jobOperator) {
        return IntegrationFlow.from("batchLaunchRequests")
            .handle(new JobLaunchingMessageHandler(jobOperator))
            .channel("batchLaunchReplies")
            .get();
    }
}
```

Keep message payload to one `JobLaunchRequest` shape that can be converted into explicit `JobParameters` before the job starts.

## Launch request mapping shape

```java
Message<JobLaunchRequest> message = MessageBuilder.withPayload(new JobLaunchRequest(importJob, new JobParametersBuilder().addString("input", "classpath:/customers.csv").toJobParameters())).setHeader("requestId", "req-42").build();
```

On the latest released Batch 6 line, prefer the `JobOperator` constructor shape shown above. If a Boot-managed older line exposes only `JobLauncher`-based launch integration in local code, treat that as a separate compatibility branch and keep the example set internally consistent.

## Feedback flow shape

```java
@ServiceActivator(inputChannel = "batchLaunchReplies")
void handleReply(JobExecution execution) {
    if (execution.getStatus() == BatchStatus.COMPLETED) {
        notifications.publishSuccess(execution.getJobInstance().getJobName(), execution.getId());
        return;
    }
    notifications.publishFailure(execution.getJobInstance().getJobName(), execution.getAllFailureExceptions());
}
```

## Verification rule

Verify one duplicate or replayed launch request does not create a new logical job instance unless the identifying parameters actually differ.

## Pitfalls

- Do not let message transport shape leak into reader, processor, or writer logic.
- Do not launch from integration channels before parameter identity and rerun semantics are settled.
- Do not emit completion feedback without the job name, execution id, and final status.
