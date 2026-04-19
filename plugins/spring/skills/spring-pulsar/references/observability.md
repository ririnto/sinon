# Spring Pulsar observability

Open this reference when metrics, tracing, or production debugging must be added.

## Minimum observability rule

Make topic name, subscription name, listener identity, and retry or DLQ outcome observable before scaling consumer concurrency.

## Logging shape

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse")
void handle(ShipmentEvent event) {
    log.info("topic=shipments subscription=warehouse shipmentId={}", event.shipmentId());
    service.handle(event);
}
```

## Observation shape

```java
@Bean
PulsarTemplateCustomizer<String> observationCustomizer(ObservationRegistry registry) {
    return builder -> builder.enableObservation(true).observationRegistry(registry);
}
```

## What to track

- send failures and retry counts
- consumer redelivery volume
- DLQ topic growth
- partition hot spots when keyed routing is used
- schema mismatch or deserialization failures
- observation spans or metrics around publish and listener processing paths

## Debugging rule

When a listener appears stuck, verify topic resolution, subscription type, redelivery policy, and partition ownership before changing application code.
