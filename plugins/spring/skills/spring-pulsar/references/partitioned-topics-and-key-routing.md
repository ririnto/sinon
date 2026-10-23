# Spring Pulsar partitioned topics and key routing

Open this reference when the task needs partitioned topics or key-based routing decisions.

```java
pulsarTemplate.newMessage(event)
    .withTopic("persistent://public/default/shipments")
    .withSchema(Schema.JSON(ShipmentEvent.class))
    .withMessageCustomizer(message -> message.key(event.shipmentId()))
    .send();
```

- Use partitioned topics only when throughput or keyed ordering justifies the operational cost.
- Decide partitions before multiple services depend on the topic shape.
