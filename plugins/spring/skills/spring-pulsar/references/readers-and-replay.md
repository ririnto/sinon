# Spring Pulsar readers and replay

Open this reference when the task needs replay readers.

```java
@PulsarReader(topics = "shipments", startMessageId = "earliest")
void replay(ShipmentEvent event) {
    audit.record(event);
}
```

- Use a reader when the application must traverse from a chosen cursor position without joining a normal subscription flow.
- Use a normal subscription for ordinary service consumption, retries, and DLQ handling.
