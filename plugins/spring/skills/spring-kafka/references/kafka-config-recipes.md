---
title: Spring Kafka Configuration Recipes
description: >-
  Additive operational notes for Spring Kafka: transactional producer nuance, consumer isolation, retry topic caveats, and listener concurrency.
---

Use this reference for operational depth beyond the basic producer/listener wiring.

## Transactional Producer Note

- configure transactional publishing deliberately when Kafka transaction semantics are required; do not imply this from normal producer wiring alone
- `KafkaTransactionManager` is the Spring-side transaction manager for Kafka-only transaction boundaries; use it when the goal is atomic produce + consume within Kafka itself
- do not assume `KafkaTransactionManager` provides DB + Kafka atomicity; coordinating a database write with a Kafka publish requires a transactional outbox, not a shared transaction manager

### KafkaTransactionManager configuration

```java
@Bean
KafkaTransactionManager<String, OrderEvent> transactionManager(ProducerFactory<String, OrderEvent> pf) {
    return new KafkaTransactionManager<>(pf);
}
```

With this in place, `@Transactional` on a listener or service method spans produce + consume within Kafka. It does not span a JPA write or any non-Kafka resource.

## Consumer Isolation Note

- when downstream readers must ignore uncommitted transactional records, set consumer isolation to `read_committed` explicitly

### read_committed consumer configuration

```java
@Bean
DefaultKafkaConsumerFactory<String, OrderEvent> consumerFactory(
        ConsumerFactory<String, OrderEvent> baseFactory) {
    Map<String, Object> props = new HashMap<>(baseFactory.getConfigurationProperties());
    props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
    // read_committed: only records from committed transactions are visible
    // read_uncommitted (default): all records including aborted ones are visible
    return new DefaultKafkaConsumerFactory<>(props);
}
```

Use `read_committed` when downstream consumers must not see partially written transactional batches.

## Retry and Dead-Letter Configuration Examples

```java
@Bean
CommonErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
            (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));

    return new DefaultErrorHandler(recoverer,
            new FixedBackOff(1000L, 3));  // 1s delay, 3 retries before DLQ
}
```

```java
// Per-exception policy: skip validation errors to DLQ immediately, retry transient ones
@Bean
CommonErrorHandler mixedErrorHandler(KafkaTemplate<String, Object> template) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);

    DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(500L, 5));
    errorHandler.addNotRetryableExceptions(ValidationException.class);
    return errorHandler;
}
```

## Listener Concurrency Note

Listener concurrency belongs in configuration only after partitioning and ordering assumptions are understood.

## Retry Topic Caveat

- use retry topics when backoff and retry routing are part of the contract, not as a generic replacement for understanding failure classes
- not every exception should be retried
- some errors belong in a dead-letter topic immediately
- poison messages need an operator path, not infinite optimism

Transaction-boundary, idempotent-consumer, and delivery-semantics depth should stay with the active Kafka guidance rather than behind another reference hop.
