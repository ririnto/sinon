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
- Use Pulsar-native DLQ with `Shared` subscriptions only.
- For `Exclusive`, `Failover`, or `Key_Shared`, use `PulsarConsumerErrorHandler` for Spring-native recovery instead.
- Validate that the chosen subscription type actually supports the retry and DLQ behavior the system expects.

## Validation rule

Verify one representative poison-message path reaches the expected dead-letter topic.

## Gotchas

- Do not choose a DLQ topic name that hides the source topic or subscription.
- Do not enable dead-letter publishing without one representative poison-message test.
- Pulsar-native dead-letter policy works only with `Shared` subscriptions.
  - For `Exclusive`, `Failover`, or `Key_Shared` subscriptions, use `PulsarConsumerErrorHandler` instead (see the error handling reference).
  - `PulsarConsumerErrorHandler` is also valid for `Shared` subscriptions when Spring-native recovery is preferred.
