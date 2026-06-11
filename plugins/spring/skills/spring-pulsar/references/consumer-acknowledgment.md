# Spring Pulsar consumer acknowledgment

Open this reference when listener acknowledgment should be controlled explicitly instead of using the ordinary listener flow.

## Acknowledgment modes

Spring Pulsar provides three acknowledgment modes:

- `BATCH` (default) -- the container acknowledges the entire batch after all records in the batch are processed.
- `RECORD` -- the container acknowledges each record individually after it is processed.
- `MANUAL` -- the application controls when each message is acknowledged.

Set the ack mode on the listener annotation or on the container properties.

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse", ackMode = AckMode.RECORD)
void handle(ShipmentEvent event) {
    service.handle(event);
}
```

## Manual acknowledgment shape

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse", ackMode = AckMode.MANUAL)
void handle(ShipmentEvent event, Acknowledgement acknowledgement) {
    service.handle(event);
    acknowledgement.acknowledge();
}
```

## Negative acknowledgment shape

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse", ackMode = AckMode.MANUAL)
void handle(ShipmentEvent event, Acknowledgement acknowledgement) {
    try {
        service.handle(event);
        acknowledgement.acknowledge();
    } catch (RuntimeException ex) {
        acknowledgement.nack();
    }
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Ordinary listener should acknowledge through the default container flow | stay on the common path |
| Listener must coordinate its acknowledgment with downstream work | explicit acknowledgment handling |
| Transaction should group acknowledgment with a write | acknowledgment plus transaction reference |

## Gotchas

- Do not move to manual acknowledgment unless the listener truly needs acknowledgment timing control.
- Do not acknowledge before the downstream side effect that defines success has completed.

## Verification rule

Verify one representative failure path actually redelivers or reaches the expected recovery flow when manual acknowledgment uses `nack()`.
