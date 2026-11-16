# Kafka implementation examples

## Producer and ordinary listener

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

## Retry topic shape

Since 4.1, `RetryTopicConfigurationBuilder` defaults `sameIntervalTopicReuseStrategy` to `SINGLE_TOPIC` (previously `MULTIPLE_TOPICS`).
This aligns the builder default with the `@RetryableTopic` annotation default.
No migration is needed for users of `@RetryableTopic`, but programmatic builder users should verify their topic naming behavior matches intent.

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

## Manual acknowledgment container intent

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

## Per-listener ack mode override

Since 4.1, `@KafkaListener` supports an `ackMode` attribute that overrides the container factory default without a separate factory bean.
Use this when most listeners share one factory but one listener needs a different acknowledgment strategy.

```java
@KafkaListener(topics = "payments", groupId = "billing", ackMode = "MANUAL")
void handle(PaymentEvent event, Acknowledgment ack) {
    processor.process(event);
    ack.acknowledge();
}
```

The attribute accepts `AckMode` enum names and supports SpEL expressions and property placeholders.

Keep the ordinary path on single-record listeners unless throughput or downstream batching requirements justify batch consumption.
Open [references/batch-listeners.md](batch-listeners.md) when the listener should actually switch to batch mode.

## Share consumer shape (KIP-932)

Share consumers allow multiple consumers in the same share group to cooperatively consume from the same partitions, with the Kafka broker distributing records at the record level.
This differs from consumer groups where each partition is exclusively assigned to one consumer.

```java
@Bean
ShareConsumerFactory<String, String> shareConsumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    return new DefaultShareConsumerFactory<>(props);
}

@Bean
ShareKafkaListenerContainerFactory<String, String> shareKafkaListenerContainerFactory(ShareConsumerFactory<String, String> shareConsumerFactory) {
    return new ShareKafkaListenerContainerFactory<>(shareConsumerFactory);
}
```

```java
@KafkaListener(topics = "order-queue", containerFactory = "shareKafkaListenerContainerFactory", groupId = "order-processors")
void handle(ConsumerRecord<String, String> record) {
    process(record.value());
}
```

Three acknowledgment modes control record delivery: `EXPLICIT` (default, container-managed), `MANUAL` (listener-driven), and `IMPLICIT` (broker auto-accept).
In `MANUAL` mode the listener accepts a `ShareAcknowledgment` parameter and must call one terminal method per record: `acknowledge()` (ACCEPT), `release()` (redelivery), or `reject()` (permanent failure).
Call `renew()` to extend the acquisition lock for long-running processing.

Share consumers do not support batch processing, topic patterns, explicit partition assignment, or the `CommonErrorHandler` interface.
Error recovery uses the `ShareConsumerRecordRecoverer` interface instead.
Open [references/share-consumers.md](share-consumers.md) when building share consumer listeners that need manual acknowledgment, error recovery, concurrency, or lifecycle integration.

## Embedded Kafka test shape

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
