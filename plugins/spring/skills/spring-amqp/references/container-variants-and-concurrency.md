# Listener container variants and concurrency

Open this reference when the default listener-container setup in [SKILL.md](../SKILL.md) is not enough and the blocker is container choice, dedicated factories, prefetch, concurrency, or ordering tradeoffs.

## Container choice blocker

**Problem:** the default listener-container baseline works functionally, but throughput, latency, or broker-consumer control requires a different container model.

**Solution:** choose the container type by delivery behavior, not by preference.

| Container | Use when |
| --- | --- |
| `SimpleMessageListenerContainer` | ordinary queue consumers with scaling and retry advice |
| `DirectMessageListenerContainer` | lower-latency direct consumer management is needed |

## Dedicated factory blocker

**Problem:** one listener needs different acknowledgment, retry, or scaling rules than the rest of the application.

**Solution:** create a dedicated factory only for the divergent listener path.

```java
@Bean
SimpleRabbitListenerContainerFactory ordersFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connectionFactory);
    factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
    factory.setPrefetchCount(50);
    factory.setConcurrentConsumers(2);
    factory.setMaxConcurrentConsumers(8);
    return factory;
}
```

Use dedicated factories sparingly. Copying near-identical factories for every listener usually hides configuration drift.

## Concurrency and ordering blocker

**Problem:** higher concurrency improves throughput but breaks ordering assumptions or overloads downstream services.

**Solution:** tune concurrency and prefetch together, then verify ordering and back-pressure behavior with realistic traffic.

- Increase `concurrentConsumers` only after confirming the handler is idempotent and order-insensitive.
- Keep `prefetchCount` low when message handling is slow or strict ordering matters.
- Prefer one consumer for order-sensitive flows unless the business contract explicitly allows reordering.

## Decision points

| Situation | Choice |
| --- | --- |
| ordinary queue consumer with scaling | `SimpleMessageListenerContainer` |
| low-latency container control | `DirectMessageListenerContainer` |
| throughput matters more than ordering | raise concurrency and prefetch carefully |
| ordering matters more than throughput | keep one consumer and conservative prefetch |

## Pitfalls

- Do not raise concurrency before checking whether the listener logic is idempotent.
- Do not change prefetch in isolation; it changes both memory pressure and fairness.
- Do not use a dedicated factory when a shared baseline is sufficient.
