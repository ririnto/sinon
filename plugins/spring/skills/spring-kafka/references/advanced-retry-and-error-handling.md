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
ConcurrentKafkaListenerContainerFactory<String, OrderEvent> kafkaListenerContainerFactory(ConsumerFactory<String, OrderEvent> consumerFactory, KafkaTemplate<String, OrderEvent> kafkaTemplate) {
    ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), new FixedBackOff(1000L, 2L));
    errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, DeserializationException.class, RecordDeserializationException.class);
    factory.setCommonErrorHandler(errorHandler);
    return factory;
}
```

## Classification rule

- Add non-retryable exceptions explicitly when the payload or schema problem cannot be fixed by waiting.
- Keep transient infrastructure exceptions on the retry path.
- Route permanent business failures directly to the recoverer or DLT path.

## Pre-listener deserialization failure shape

```yaml
spring:
  kafka:
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.value.default.type: "com.example.events.OrderEvent"
        spring.json.trusted.packages: "com.example.events"
```

```java
@Bean
DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, byte[]> kafkaTemplate) {
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), new FixedBackOff(0L, 0L));
    errorHandler.addNotRetryableExceptions(DeserializationException.class, RecordDeserializationException.class);
    return errorHandler;
}
```

Use `ErrorHandlingDeserializer` or an equivalently explicit consumer-side setup so malformed payloads become records that the container error handler can route to recovery instead of failing before the configured path is reachable.

## Verification rule

Verify one representative permanent failure goes directly to the DLT and one transient failure is retried before recovery so the classification policy is visible in tests, not only configuration.

## Decision points

| Situation | Use |
| --- | --- |
| Retry with DLT topics and ordinary listener code | stay on the common path in `SKILL.md` |
| Container-level recovery or recoverer policy | `DefaultErrorHandler` |
| Permanent failure should skip retries | direct dead-letter or recoverer path |
