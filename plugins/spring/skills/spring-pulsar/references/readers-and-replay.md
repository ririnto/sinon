# Spring Pulsar readers and replay

Open this reference when the task needs replay readers.

## Reader boundary

- Use a reader when the application must traverse from a chosen cursor position without joining a normal subscription flow.
- Use a normal subscription for ordinary service consumption, retries, and DLQ handling.
- Keep replay readers operationally separate from production listeners so replay does not look like ordinary processing.

```java
@PulsarReader(topics = "shipments", startMessageId = "earliest")
void replay(ShipmentEvent event) {
    audit.record(event);
}
```

## Replay verification shape

```java
@Test
void replaysFromEarliest() {
    replayAudit.clear();
    readerContainer.start();
    assertThat(replayAudit).contains("shipment-1", "shipment-2");
}
```

## Gotchas

- Do not switch to readers just to avoid choosing a subscription type.
- Do not assume reader replay honors the same retry or dead-letter semantics as listeners.
