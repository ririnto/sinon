# Spring Pulsar producer and consumer customizers and properties

Open this reference when `spring.pulsar.*` properties are not enough or builder customizers are required.

## Prefer properties first

```yaml
spring:
  pulsar:
    producer:
      batching-enabled: true
      chunking-enabled: false
    consumer:
      ack:
        timeout: 30s
      dead-letter-policy:
        max-redeliver-count: 5
```

## Producer customizer shape

```java
@Bean
ProducerBuilderCustomizer<ShipmentEvent> shipmentProducerCustomizer() {
    return builder -> builder.blockIfQueueFull(true);
}
```

## Consumer customizer shape

```java
@Bean
ConsumerBuilderCustomizer<ShipmentEvent> shipmentConsumerCustomizer() {
    return builder -> builder.consumerName("warehouse-consumer");
}
```

## Selection rule

- Use `spring.pulsar.*` properties for stable deployment-level defaults.
- Use builder customizers when one producer, listener, or reader needs behavior the global properties should not force on every client.
- Drop to direct Pulsar client APIs only when both properties and Spring customizers cannot express the requirement.
