# Spring Pulsar consumer acknowledgment

Open this reference when listener acknowledgment should be controlled explicitly instead of using the ordinary listener flow.

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
