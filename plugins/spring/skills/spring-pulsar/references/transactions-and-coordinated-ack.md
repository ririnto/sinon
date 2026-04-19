# Spring Pulsar transactions and coordinated acknowledgment

Open this reference when grouped writes or acknowledgment coordination are required.

## Transaction shape

```java
pulsarTemplate.executeInTransaction(operations -> {
    operations.send("shipments", event);
    operations.send("shipment-audit", auditEvent);
    return null;
});
```

## Coordinated acknowledgment shape

```java
pulsarTemplate.executeInTransaction(operations -> {
    operations.send("shipments", event);
    acknowledgment.acknowledge();
    return null;
});
```

## Decision points

- Use transactions only when grouped Pulsar writes or coordinated acknowledgment semantics are truly required.
- Verify the producer and consumer flow actually share the same transaction boundary assumptions.
