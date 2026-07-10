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

## Producer caching

The producer factory caches producers in an LRU fashion.
Customizers are included in the cache key.
Lambda customizers require careful handling because `equals`/`hashCode` for Lambdas depends on whether they share the same instance and whether they capture variables from outside their closure.

Safe: an inline lambda with no external closure variables can match the producer cache key.

```java
void sendUser() {
    var user = randomUser();
    template.newMessage(user)
        .withTopic("user-topic")
        .withProducerCustomizer((b) -> b.producerName("user"))
        .send();
}
```

Unsafe: a lambda that captures `name` can miss the producer cache on every call.

```java
void sendUser() {
    var user = randomUser();
    var name = randomName();
    template.newMessage(user)
        .withTopic("user-topic")
        .withProducerCustomizer((b) -> b.producerName(name))
        .send();
}
```

When the Lambda does not meet these conditions, provide a customizer implementation with a valid `equals`/`hashCode` to avoid cache misses and degraded performance.

## Selection rule

- Use `spring.pulsar.*` properties for stable deployment-level defaults.
- Use builder customizers when one producer, listener, or reader needs behavior the global properties should not force on every client.
- Drop to direct Pulsar client APIs only when both properties and Spring customizers cannot express the requirement.
