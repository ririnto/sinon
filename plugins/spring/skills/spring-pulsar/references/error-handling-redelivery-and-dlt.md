# Spring Pulsar error handling, redelivery, and DLT

Open this reference when redelivery, backoff, or dead-letter publishing behavior must be tuned.

## Error handler shape

```java
@Bean
PulsarMessageRecovererFactory<ShipmentEvent> shipmentRecovererFactory(PulsarTemplate<ShipmentEvent> pulsarTemplate) {
    return () -> new PulsarDeadLetterPublishingRecoverer<>(pulsarTemplate, event -> "shipments-dlt");
}
```

## Redelivery backoff shape

```java
MultiplierRedeliveryBackoff backoff = MultiplierRedeliveryBackoff.builder()
    .minDelayMs(1_000)
    .maxDelayMs(30_000)
    .multiplier(2.0)
    .build();
```

## Selection rule

- Use plain redelivery first when the listener can recover after a short transient failure.
- Use Pulsar redelivery backoff and dead-letter policy when retries should be isolated operationally before final DLT handling.
- Use DLT publishing when the failed message needs operator review, replay, or a separate compensating workflow.
- Keep one representative failure test that proves the message lands in the expected retry or dead-letter path.

## Operational warning

Redelivery, backoff, and dead-letter publishing change latency and retry pressure together. Tune them as one policy rather than as isolated flags.
