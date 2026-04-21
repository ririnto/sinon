# Spring Pulsar dead-letter policy

Open this reference when DLQ behavior must be chosen explicitly.

## Policy shape

```java
DeadLetterPolicy deadLetterPolicy = DeadLetterPolicy.builder()
    .maxRedeliverCount(5)
    .deadLetterTopic("shipments-dlt")
    .build();
```

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse", deadLetterPolicy = "shipmentDeadLetterPolicy")
void handle(ShipmentEvent event) {
    service.handle(event);
}

@Bean
DeadLetterPolicy shipmentDeadLetterPolicy() {
    return DeadLetterPolicy.builder().maxRedeliverCount(5).deadLetterTopic("shipments-dlt").build();
}
```

- Treat DLQ as an explicit operational path, not an automatic default.
- Keep DLQ topic naming and retention rules visible to operators before rollout.
- Use DLQ with subscription modes that support redelivery and dead-letter routing, such as `Shared`. Do not expect `Exclusive` or `Failover` subscriptions to provide the same DLQ behavior.
- Validate that the chosen subscription type actually supports the retry and DLQ behavior the system expects.

## Validation rule

Verify one representative poison-message path reaches the expected dead-letter topic.

## Gotchas

- Do not choose a DLQ topic name that hides the source topic or subscription.
- Do not enable dead-letter publishing without one representative poison-message test.
