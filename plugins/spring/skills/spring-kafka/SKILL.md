---
metadata:
  reference:
    Spring for Apache Kafka:
      version: 4.1.1
      url:
        - https://docs.spring.io/spring-kafka/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/kafka/spring-kafka/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/kafka/spring-kafka/4.1.1/spring-kafka-4.1.1.pom
    Spring Boot 4.1:
      version: 4.1.1
      url:
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom
name: spring-kafka
description: >-
  Use Spring Kafka for producers, listeners, delivery, retries, dead letters, and transactions.
---

# Spring for Apache Kafka

## Boundaries

Use `spring-kafka` for Kafka producers, consumers, listener containers, offsets, retry topics, dead-letter topics, and Kafka-specific testing.

- RabbitMQ or Pulsar semantics and client APIs are outside this skill's scope.
- Keep transport concerns in producer and listener boundaries.
  - Domain logic should not know about offsets or Kafka headers.

## Core decisions

| Situation | Use |
| --- | --- |
| Application sends records into Kafka | `KafkaTemplate` |
| Application consumes records from Kafka | `@KafkaListener` |
| Listener should commit after successful processing with the ordinary container flow | container-managed acknowledgment |
| Listener must control when acknowledgment is requested instead of using the ordinary container-managed path | manual acknowledgment |
| Listener should process many records together | batch listener |
| Ordinary failure path needs delayed reprocessing and a DLT | `@RetryableTopic` |
| Listener must override container factory ack mode without a separate factory | `@KafkaListener(ackMode = ...)` |
| Multiple consumers cooperatively consume from the same partitions | share consumer (KIP-932) |

Keep the default path small: one producer, one single-record listener, one serialization strategy per topic, container-managed acknowledgment unless explicit offset control is required, and one explicit retry/DLT policy.
Use batch listeners only when throughput or downstream batching requirements justify them.
When using programmatic retry topic builders or share consumers, read their [implementation guidance](references/implementation-examples.md#retry-topic-shape) for 4.1 defaults and [share-consumer constraints](references/implementation-examples.md#share-consumer-shape-kip-932) respectively.

## Dependency baseline

Before recommending a new version, check each selected Maven artifact on Maven Central for its latest stable release compatible with the project's Boot and Framework lines.
Keep project BOM or version-catalog management and existing pins unless the task authorizes changing them.

Use Spring Kafka for application code and the Kafka test module for integration tests.

The Spring Kafka `4.1.x` examples require Spring Framework 7.0.x and Apache Kafka `kafka-clients` 4.2.x.
Spring Boot `4.0.x` manages Spring Kafka `4.0.x`.
Spring Boot `3.5.x` and `3.4.x` manage Spring Kafka `3.3.x`.
Applications on Boot 3.4.x or earlier must stay on the `3.3.x` line and should be treated as a separate compatibility branch.

For dependency baseline, see [example 1](references/configuration-examples.md#example-1-dependency-baseline).

## First safe configuration

### Topic declaration shape

```java
@Bean
NewTopic paymentsTopic() {
    return TopicBuilder.name("payments")
        .partitions(3)
        .replicas(1)
        .build();
}
```

### JSON serializer shape

For JSON serializer shape, see [example 2](references/configuration-examples.md#example-2-json-serializer-shape).

## Coding procedure

1. Keep topic names, keys, and consumer groups explicit and stable.
2. Use one serializer and deserializer strategy per topic unless interop forces otherwise.
3. Choose acknowledgment mode deliberately and do not assume defaults match delivery guarantees.
4. Keep listeners idempotent because retries and rebalances can re-deliver records.
5. Choose retry topics or dead-letter topics before enabling concurrency at scale.
6. Decide between consumer groups (partition-exclusive) and share groups (cooperative, record-level distribution) based on whether listeners need partition isolation or cooperative consumption.
7. For listener delivery changes, test successful handling and affected retry or dead-letter paths.

## Failure classification

- Retry transient infrastructure failures.
- Dead-letter permanent business failures.
- Treat serialization or schema failures as configuration or contract problems first, not as ordinary business retries.

## Implementation examples

For producer, listener, retry, acknowledgment, share consumer, or embedded test examples, read [implementation examples](references/implementation-examples.md).

## Output and configuration shapes

### Topic name shape

```text
payments
orders
orders-dlt
```

### Consumer group shape

```text
billing
```

### Retry topic annotation shape

```java
@RetryableTopic(attempts = "4")
```

## Testing checklist

Select checks for the changed contract and regression risks.
Use the existing native test setup.

- Verify producers write to the intended topic with the expected key.
- Verify listeners deserialize the record shape that producers send.
- Verify retry and DLT behavior on one representative failure path.
- Verify acknowledgment mode matches the intended delivery guarantee.
- Verify topic declarations and serializer settings stay aligned with production configuration.

## Production checklist

- Keep topic names, keys, and consumer groups stable after other systems depend on them.
- Make listeners idempotent because rebalances and retries can replay records.
- Distinguish transient retryable failures from permanent business failures.
- Keep dead-letter topics observable and operationally documented.
- Bound concurrency and partitioning decisions to the actual ordering and throughput needs.

## References

- Open [references/transactions.md](references/transactions.md) when the application needs atomic consume-produce workflows.
- Open [references/exactly-once-semantics.md](references/exactly-once-semantics.md) when the workflow must combine Kafka transactions with exactly-once delivery expectations.
- Open [references/batch-listeners.md](references/batch-listeners.md) when the consumer should process `List<T>` batches instead of one record at a time.
- Open [references/share-consumers.md](references/share-consumers.md) when building share consumer listeners (KIP-932) that need manual acknowledgment, error recovery, concurrency configuration, or container integration.
- Open [references/deserialization-failures.md](references/deserialization-failures.md) when bad payloads fail before listener code runs and retry or DLT policy must still be intentional.
- Open [references/listener-replay.md](references/listener-replay.md) when the consumer must seek or replay records deliberately.
- Open [references/tombstones.md](references/tombstones.md) when the topic uses compacted-record delete semantics.
- Open [references/advanced-retry-and-error-handling.md](references/advanced-retry-and-error-handling.md) when `@RetryableTopic` is not enough and the listener needs explicit `DefaultErrorHandler`, recoverers, or deeper retry classification.
