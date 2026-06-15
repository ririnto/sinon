# Spring Pulsar error handling, redelivery, and DLT

Open this reference when redelivery, backoff, or dead-letter publishing behavior must be tuned.

## Error handler shape

```java
@Bean
PulsarMessageRecovererFactory<ShipmentEvent> shipmentRecovererFactory(PulsarTemplate<ShipmentEvent> pulsarTemplate) {
    return () -> new PulsarDeadLetterPublishingRecoverer<>(pulsarTemplate, event -> "shipments-dlt");
}
```

## Redelivery backoff shape

```java
MultiplierRedeliveryBackoff backoff = MultiplierRedeliveryBackoff.builder()
    .minDelayMs(1_000)
    .maxDelayMs(30_000)
    .multiplier(2.0)
    .build();
```

## PulsarConsumerErrorHandler

Pulsar's native `DeadLetterPolicy` works only with `Shared` subscriptions.
For `Exclusive`, `Failover`, or `Key_Shared` subscriptions, use `PulsarConsumerErrorHandler` instead.

```java
@PulsarListener(
    topics = "shipments",
    subscriptionName = "warehouse",
    subscriptionType = SubscriptionType.Exclusive,
    pulsarConsumerErrorHandler = "shipmentErrorHandler"
)
void handle(ShipmentEvent event) {
    service.handle(event);
}

@Bean
PulsarConsumerErrorHandler<ShipmentEvent> shipmentErrorHandler(PulsarTemplate<ShipmentEvent> template) {
    return new DefaultPulsarConsumerErrorHandler<>(
        new PulsarDeadLetterPublishingRecoverer<>(template, event -> "shipments-dlt"),
        backoff
    );
}
```

The error handler retries the failed message, then sends it to the DLT when retries are exhausted.
For batch listeners, the container retries the entire batch starting from the failed message.

## Acknowledgment timeout redelivery

```java
@PulsarListener(
    topics = "shipments",
    subscriptionName = "warehouse",
    subscriptionType = SubscriptionType.Shared,
    ackTimeoutMillis = 60_000
)
void handle(ShipmentEvent event) {
    service.handle(event);
}
```

When ack timeout has a value above zero and the consumer does not acknowledge within that period, Pulsar redelivers the message.

## Negative acknowledgment redelivery

```java
@PulsarListener(
    topics = "shipments",
    subscriptionName = "warehouse",
    subscriptionType = SubscriptionType.Shared,
    negativeAckRedeliveryBackoff = "redeliveryBackoff",
    properties = {"negativeAckRedeliveryDelay=10ms"}
)
void handle(ShipmentEvent event) {
    if (event.isInvalid()) {
        throw new RuntimeException("fail " + event);
    }
    service.handle(event);
}
```

## Selection rule

- Use plain redelivery first when the listener can recover after a short transient failure.
- Use PulsarConsumerErrorHandler for Spring-native DLQ that works across all subscription types.
- Use Pulsar-native dead-letter policy when only Shared subscriptions are in use and native Pulsar behavior is preferred.
- Use DLT publishing when the failed message needs operator review, replay, or a separate compensating workflow.
- Keep one representative failure test that proves the message lands in the expected retry or dead-letter path.

## Operational warning

Redelivery, backoff, and dead-letter publishing change latency and retry pressure together.
Tune them as one policy rather than as isolated flags.
