# Spring Kafka deserialization failures

Open this reference when bad payloads fail before listener code runs and retry or DLT policy must still be intentional.

## Failure boundary

Treat deserialization failures as transport or contract problems that can happen before the listener method receives a domain object.

## Error-handling deserializer shape

```yaml
spring:
  kafka:
    consumer:
      key-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.key.delegate.class: org.apache.kafka.common.serialization.StringDeserializer
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
```

## Recoverer handling shape

```java
@Bean
ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
        ConsumerFactory<String, Object> consumerFactory,
        KafkaTemplate<String, Object> kafkaTemplate) {
    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(new DefaultErrorHandler(
        new DeadLetterPublishingRecoverer(kafkaTemplate),
        new FixedBackOff(0L, 0L)
    ));
    return factory;
}
```

Pair this with a producer or template configuration that can publish the failed payload format the recoverer will send to the DLT, especially when malformed bytes or raw values are forwarded instead of a successfully deserialized domain object.

## Guardrails

- Do not treat deserialization failures as ordinary business retries.
- Route bad payloads intentionally, because listener code cannot acknowledge or classify a record it never successfully deserialized.
- Keep serializer and deserializer settings aligned with the producer contract before adding recoverer logic.
