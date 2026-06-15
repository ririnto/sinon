# Listener container variants and concurrency

Open this reference when the default listener-container setup in [`SKILL.md`](../SKILL.md) is not enough and the blocker is container choice, dedicated factories, prefetch, concurrency, or ordering tradeoffs.

## Container choice blocker

Problem: the default listener-container baseline works functionally, but throughput, latency, or broker-consumer control requires a different container model.

Solution: choose the container type by delivery behavior, not by preference.

| Container | Use when |
| --- | --- |
| `SimpleMessageListenerContainer` | ordinary queue consumers with scaling and retry advice |
| `DirectMessageListenerContainer` | lower-latency direct consumer management is needed |

## Dedicated factory blocker

Problem: one listener needs different acknowledgment, retry, or scaling rules than the rest of the application.

Solution: create a dedicated factory only for the divergent listener path.

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

Use dedicated factories sparingly.
Copying near-identical factories for every listener usually hides configuration drift.

## Concurrency and ordering blocker

Problem: higher concurrency improves throughput but breaks ordering assumptions or overloads downstream services.

Solution: tune concurrency and prefetch together, then verify ordering and back-pressure behavior with realistic traffic.

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

## Immediate scale-down (4.1)

Problem: `SimpleMessageListenerContainer` scales consumers up under load but retains idle consumers for too long, wasting broker resources.

Solution: set `immediateScaleDown(true)` on the container or factory.
Each consumer checks after every message whether it is in excess and stops itself when processing completes, rather than waiting for idle-trigger or stop-interval timeouts.

```java
factory.getContainerProperties().setImmediateScaleDown(true);
```

This option bypasses `consecutiveIdleTrigger` and `stopConsumerMinInterval` for scale-down decisions.
Scale-up behavior is unaffected.
Requires `maxConcurrentConsumers` to be configured.

## Error handler fatal-stop option (4.1)

Problem: fatal exceptions in a listener cause message rejection and requeue loops because the container keeps consuming from a broken state.

Solution: use `ConditionalRejectingErrorHandler` with `stopListenerOnFatal(true)`.
Fatal exceptions throw `FatalListenerExecutionException` instead of `AmqpRejectAndDontRequeueException`, stopping the container and requeuing the message for other consumers.

```java
ConditionalRejectingErrorHandler errorHandler = new ConditionalRejectingErrorHandler();
errorHandler.setStopListenerOnFatal(true);
factory.setErrorHandler(errorHandler);
```
