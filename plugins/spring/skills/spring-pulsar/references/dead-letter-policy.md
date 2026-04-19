# Spring Pulsar dead-letter policy

Open this reference when DLQ behavior must be chosen explicitly.

```java
DeadLetterPolicy deadLetterPolicy = DeadLetterPolicy.builder()
    .maxRedeliverCount(5)
    .deadLetterTopic("shipments-dlt")
    .build();
```

- Treat DLQ as an explicit operational path, not an automatic default.
- Keep DLQ topic naming and retention rules visible to operators before rollout.
- Validate that the chosen subscription type actually supports the retry and DLQ behavior the system expects.

## Validation rule

Verify one representative poison-message path reaches the expected dead-letter topic.
