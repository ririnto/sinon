# Spring Pulsar subscription types and concurrency

Open this reference when subscription mode or listener concurrency must be chosen.

## Shared subscription shape

```java
@PulsarListener(
    topics = "shipments",
    subscriptionName = "warehouse",
    subscriptionType = SubscriptionType.Shared,
    concurrency = "3"
)
void handle(ShipmentEvent event) {
    service.handle(event);
}
```

## Key-shared subscription shape

```java
@PulsarListener(
    topics = "shipments",
    subscriptionName = "warehouse",
    subscriptionType = SubscriptionType.Key_Shared,
    concurrency = "3"
)
void handle(ShipmentEvent event) {
    service.handle(event);
}
```

## Decision points

- Use `Exclusive` when one active consumer must preserve the simplest ordering model.
- Use `Shared` when parallel consumption matters more than strict per-topic ordering.
- Use `Failover` when one active consumer is required with a standby consumer ready to take over.
- Use `Key_Shared` only when message keys are stable and key-based ordering matters.
