# Spring Pulsar partitioned topics and key routing

Open this reference when the task needs partitioned topics or key-based routing decisions.

```java
pulsarTemplate.newMessage(Schema.JSON(ShipmentEvent.class))
    .withTopic("persistent://public/default/shipments")
    .withMessageCustomizer(message -> message.key(event.shipmentId()))
    .withValue(event)
    .send();
```

- Use partitioned topics only when throughput or keyed ordering justifies the operational cost.
- Decide partitions before multiple services depend on the topic shape.
