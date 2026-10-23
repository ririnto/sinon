# Spring Pulsar tombstones

Open this reference when tombstone records are required.

```java
void sendTombstone(PulsarTemplate<byte[]> pulsarTemplate, String shipmentId) {
    pulsarTemplate.newMessage(null)
        .withTopic("shipments")
        .withSchema(Schema.AUTO_PRODUCE_BYTES())
        .withMessageCustomizer(message -> message.key(shipmentId))
        .send();
}
```

- Treat tombstones as a compacted-topic concern.
- Verify downstream consumers understand null payload handling before producing tombstones.
