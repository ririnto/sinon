---
name: "spring-kafka"
description: "Build Kafka producers and consumers in Spring with `KafkaTemplate`, `@KafkaListener`, topic declarations, retries, dead-letter topics, acknowledgment strategies, and embedded Kafka tests. Use this skill when building Kafka producers and consumers in Spring with `KafkaTemplate`, `@KafkaListener`, topic declarations, retries, dead-letter topics, acknowledgment strategies, and embedded Kafka tests."
metadata:
  title: "Spring for Apache Kafka"
  official_project_url: "https://spring.io/projects/spring-kafka"
  reference_doc_urls:
    - "https://docs.spring.io/spring-kafka/reference/index.html"
  version: "4.0.4"
---

Use this skill when building Kafka producers and consumers in Spring with `KafkaTemplate`, `@KafkaListener`, topic declarations, retries, dead-letter topics, acknowledgment strategies, and embedded Kafka tests.

## Boundaries

Use `spring-kafka` for Kafka producers, consumers, listener containers, offsets, retry topics, dead-letter topics, and Kafka-specific testing.

- Use `spring-amqp` or `spring-pulsar` for RabbitMQ or Pulsar semantics and client APIs.
- Keep transport concerns in producer and listener boundaries. Domain logic should not know about offsets or Kafka headers.

## Common path

The ordinary Spring Kafka job is:

1. Define the topic names, key strategy, and consumer group first.
2. Add Spring Kafka and keep serialization format explicit.
3. Publish through `KafkaTemplate` and consume through `@KafkaListener`, defaulting to container-managed acknowledgment unless offset control requires a manual strategy.
4. Decide retry and dead-letter behavior before production rollout.
5. Add an embedded Kafka or equivalent integration test that proves producer and consumer agreement.

## Core decisions

| Situation | Use |
| --- | --- |
| Application sends records into Kafka | `KafkaTemplate` |
| Application consumes records from Kafka | `@KafkaListener` |
| Listener should commit after successful processing with the ordinary container flow | container-managed acknowledgment |
| Listener must control when acknowledgment is requested instead of using the ordinary container-managed path | manual acknowledgment |
| Listener should process many records together | batch listener |
| Ordinary failure path needs delayed reprocessing and a DLT | `@RetryableTopic` |

Keep the default path small: one producer, one listener, one serialization strategy per topic, container-managed acknowledgment unless explicit offset control is required, and one explicit retry/DLT policy.

## Dependency baseline

Use Spring Kafka for application code and the Kafka test module for integration tests.

The current stable Spring Kafka line is `4.0.4`. The `4.1.x` line is still milestone-only and should be treated as upcoming until it reaches GA. Spring Boot `4.0.x` manages Spring Kafka `4.0.x`; older Spring Boot `3.5.x` and `3.4.x` applications remain on the `3.3.x` line and should be treated as a separate compatibility branch.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

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

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.example.events"
        spring.json.value.default.type: "com.example.events.PaymentEvent"
```

## Coding procedure

1. Keep topic names, keys, and consumer groups explicit and stable.
2. Use one serializer and deserializer strategy per topic unless interop forces otherwise.
3. Choose acknowledgment mode deliberately and do not assume defaults match delivery guarantees.
4. Keep listeners idempotent because retries and rebalances can re-deliver records.
5. Choose retry topics or dead-letter topics before enabling concurrency at scale.
6. Test both successful handling and one representative retry or dead-letter path.

## Failure classification

- Retry transient infrastructure failures.
- Dead-letter permanent business failures.
- Treat serialization or schema failures as configuration or contract problems first, not as ordinary business retries.

## Implementation examples

### Producer and ordinary listener

```java
@Service
class PaymentPublisher {
    private final KafkaTemplate<String, PaymentEvent> kafka;

    PaymentPublisher(KafkaTemplate<String, PaymentEvent> kafka) {
        this.kafka = kafka;
    }

    CompletableFuture<SendResult<String, PaymentEvent>> publish(PaymentEvent event) {
        return kafka.send("payments", event.paymentId(), event);
    }
}

@Component
class PaymentListener {
    private final PaymentProcessor processor;
    private final AtomicReference<String> lastSeenPaymentId = new AtomicReference<>();

    PaymentListener(PaymentProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = "payments", groupId = "billing")
    void handle(PaymentEvent event) {
        processor.process(event);
        lastSeenPaymentId.set(event.paymentId());
    }

    String lastSeenPaymentId() {
        return lastSeenPaymentId.get();
    }
}
```

### Retry topic shape

```java
@Configuration
@EnableKafkaRetryTopic
class KafkaRetryConfiguration {
}

@RetryableTopic(attempts = "4", backoff = @Backoff(delay = 1000, multiplier = 2.0))
@KafkaListener(topics = "orders")
void process(OrderEvent event) {
    service.process(event);
}

@DltHandler
void deadLetter(OrderEvent event) {
    audit.failed(event);
}
```

### Manual acknowledgment container intent

```java
@Bean
ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> manualAckKafkaListenerContainerFactory(ConsumerFactory<String, PaymentEvent> consumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    return factory;
}

@KafkaListener(topics = "payments", groupId = "billing", containerFactory = "manualAckKafkaListenerContainerFactory")
void handle(PaymentEvent event, Acknowledgment ack) {
    processor.process(event);
    ack.acknowledge();
}
```

Use manual acknowledgment only when listener code must control the commit point explicitly.

Keep the ordinary path on single-record listeners unless throughput or downstream batching requirements justify batch consumption. Open [references/batch-listeners.md](references/batch-listeners.md) when the listener should actually switch to batch mode.

### Embedded Kafka test shape

```java
@SpringBootTest
@EmbeddedKafka(topics = "payments", partitions = 1)
class PaymentFlowTests {
    @Autowired
    PaymentPublisher publisher;

    @Autowired
    PaymentListener listener;

    @Test
    void producerAndListenerAgreeOnThePaymentEventShape() {
        publisher.publish(new PaymentEvent("p-1"));
        await().untilAsserted(() -> assertThat(listener.lastSeenPaymentId()).isEqualTo("p-1"));
    }
}
```

## Output and configuration shapes

Return these artifacts for the ordinary path:

1. One producer or gateway entry point
2. One listener or listener-container configuration path
3. One explicit retry or DLT policy
4. One integration test that proves producer, broker, and listener agreement

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
- Open [references/deserialization-failures.md](references/deserialization-failures.md) when bad payloads fail before listener code runs and retry or DLT policy must still be intentional.
- Open [references/listener-replay.md](references/listener-replay.md) when the consumer must seek or replay records deliberately.
- Open [references/tombstones.md](references/tombstones.md) when the topic uses compacted-record delete semantics.
- Open [references/advanced-retry-and-error-handling.md](references/advanced-retry-and-error-handling.md) when `@RetryableTopic` is not enough and the listener needs explicit `DefaultErrorHandler`, recoverers, or deeper retry classification.
