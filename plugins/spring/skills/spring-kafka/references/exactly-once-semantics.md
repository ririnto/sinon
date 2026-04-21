# Spring Kafka exactly-once semantics

Open this reference when the workflow must combine Kafka transactions with exactly-once delivery expectations rather than ordinary at-least-once listener behavior.

## EOS boundary

Use exactly-once semantics only when the producer, listener container, and downstream processing rules all need transactional coordination.

## Transactional producer baseline

```yaml
spring:
  kafka:
    producer:
      transaction-id-prefix: orders-tx-
```

## Exactly-once container shape

```java
@Bean
ConcurrentKafkaListenerContainerFactory<String, OrderEvent> eosKafkaListenerContainerFactory(ConsumerFactory<String, OrderEvent> consumerFactory, KafkaTransactionManager<String, OrderEvent> transactionManager) {
    ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.getContainerProperties().setKafkaAwareTransactionManager(transactionManager);
    return factory;
}
```

## Consume-produce verification shape

```java
@KafkaListener(topics = "orders", containerFactory = "eosKafkaListenerContainerFactory")
@Transactional
void handle(OrderEvent event) {
    kafkaTemplate.send("orders-out", event.id(), new ProcessedOrderEvent(event.id()));
}
```

## Guardrails

- Do not treat exactly-once semantics as a substitute for idempotent business logic.
- Keep retry, DLT, and transactional boundaries aligned so the same record does not escape one policy and enter another unexpectedly.
- Prefer the ordinary at-least-once path unless the workflow truly needs transactional consume-produce coordination.

## Verification rule

Verify one integration test proves a consumed record and its produced follow-up record commit or roll back together instead of treating transaction enablement as sufficient evidence.
