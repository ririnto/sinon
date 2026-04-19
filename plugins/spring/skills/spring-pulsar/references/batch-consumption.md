# Spring Pulsar batch consumption

Open this reference when the listener should consume batches instead of one message at a time.

## Batch listener shape

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse", batch = true)
void handleBatch(List<ShipmentEvent> events) {
    events.forEach(service::handle);
}
```

## Batch guardrails

- Keep the ordinary path on single-message listeners unless throughput or downstream APIs naturally work in batches.
- Re-check retry, redelivery, and dead-letter behavior because the failure boundary changes when a batch is processed together.
- Verify schema handling and listener concurrency with the same batch settings used in production.

## Decision point

Use batch listeners only when the consumer logic or downstream API genuinely works in batches rather than forcing single-message logic into grouped delivery.
