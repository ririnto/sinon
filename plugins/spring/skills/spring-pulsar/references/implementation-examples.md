# Pulsar implementation examples

## Producer and listener

```java
@Service
class ShipmentPublisher {
    private final PulsarTemplate<ShipmentEvent> pulsar;

    ShipmentPublisher(PulsarTemplate<ShipmentEvent> pulsar) {
        this.pulsar = pulsar;
    }

    void publish(ShipmentEvent event) {
        pulsar.send("shipments", event);
    }
}

@Component
class ShipmentListener {
    private final ShipmentService service;

    ShipmentListener(ShipmentService service) {
        this.service = service;
    }

    @PulsarListener(topics = "shipments", subscriptionName = "warehouse")
    void handle(ShipmentEvent event) {
        service.handle(event);
    }
}
```

## Schema and subscription shape

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse", schemaType = SchemaType.JSON, subscriptionType = SubscriptionType.Shared, concurrency = "3")
void handle(ShipmentEvent event) {
    service.handle(event);
}
```

## Reader shape

```java
@PulsarReader(topics = "shipments", startMessageId = "earliest")
void replay(ShipmentEvent event) {
    audit.record(event);
}
```

## Transactional intent shape

```java
pulsar.executeInTransaction(operations -> {
    operations.send("shipments", event);
    return null;
});
```

Use transactions only when the workflow truly needs grouped Pulsar writes.
