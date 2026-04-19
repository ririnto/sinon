# Spring Kafka advanced retry and error handling

Open this reference when `@RetryableTopic` is not enough and the listener needs explicit `DefaultErrorHandler`, recoverers, or deeper retry classification.

## Retry classification

Distinguish between:

- transient infrastructure failures
- serialization or schema failures
- permanent business-rule failures

Those categories often need different retry, skip, or dead-letter outcomes.

Stay on the ordinary path in `SKILL.md` when `@RetryableTopic` is enough.

## Container error-handler shape

```java
@Bean
ConcurrentKafkaListenerContainerFactory<String, OrderEvent> kafkaListenerContainerFactory(
        ConsumerFactory<String, OrderEvent> consumerFactory,
        KafkaTemplate<String, OrderEvent> kafkaTemplate) {
    ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(new DefaultErrorHandler(
        new DeadLetterPublishingRecoverer(kafkaTemplate),
        new FixedBackOff(1000L, 2L)
    ));
    return factory;
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Retry with DLT topics and ordinary listener code | stay on the common path in `SKILL.md` |
| Container-level recovery or recoverer policy | `DefaultErrorHandler` |
| Permanent failure should skip retries | direct dead-letter or recoverer path |
