# Spring Pulsar consumer acknowledgment

Open this reference when listener acknowledgment should be controlled explicitly instead of using the ordinary listener flow.

## Manual acknowledgment shape

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse")
void handle(ShipmentEvent event, Acknowledgment acknowledgment) {
    service.handle(event);
    acknowledgment.acknowledge();
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Ordinary listener should acknowledge through the default container flow | stay on the common path |
| Listener must coordinate its acknowledgment with downstream work | explicit acknowledgment handling |
| Transaction should group acknowledgment with a write | acknowledgment plus transaction reference |
