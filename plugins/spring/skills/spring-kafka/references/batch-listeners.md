# Spring Kafka batch listeners

Open this reference when the consumer should process `List<T>` batches instead of one record at a time.

## Batch listener boundary

Use batch listeners when throughput or downstream APIs naturally operate on groups of records rather than single events.

## Batch listener shape

```java
@Bean
ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> batchKafkaListenerContainerFactory(
        ConsumerFactory<String, PaymentEvent> consumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(true);
    return factory;
}

@KafkaListener(topics = "payments-batch", batch = "true", containerFactory = "batchKafkaListenerContainerFactory")
void handleBatch(List<PaymentEvent> events) {
    events.forEach(processor::process);
}
```

## Batch guardrails

- Decide whether acknowledgment and retries should apply per batch or per record before switching away from the ordinary single-record listener path.
- Re-check dead-letter behavior when one record in a batch fails, because the failure boundary is no longer identical to the single-record path.
- Keep the batch factory explicit so batch semantics do not leak into unrelated listeners.

## Decision points

| Situation | Use |
| --- | --- |
| One record should be handled independently | ordinary single-record listener |
| Downstream processing naturally works on groups of records | batch listener |
| Retry, replay, or dead-letter handling must stay per record | prefer the single-record path unless batch semantics are mandatory |
