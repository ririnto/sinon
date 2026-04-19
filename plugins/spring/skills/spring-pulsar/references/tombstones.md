# Spring Pulsar tombstones

Open this reference when tombstone records are required.

```java
pulsarTemplate.newMessage(Schema.AUTO_PRODUCE_BYTES())
    .withTopic("shipments")
    .withKey(shipmentId)
    .withValue(null)
    .send();
```

- Treat tombstones as a compacted-topic concern.
- Verify downstream consumers understand null payload handling before producing tombstones.
