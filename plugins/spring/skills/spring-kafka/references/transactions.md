# Spring Kafka transactions

Open this reference when the application needs atomic consume-produce workflows.

## Transactions boundary

Use Kafka transactions only when the workflow truly needs atomic producer or consumer-producer coordination.

- Good fit: one handler reads a record and publishes derived records atomically.
- Poor fit: simple fire-and-forget publishing.

## `KafkaTransactionManager` shape

Enable transactional producer support first:

```yaml
spring:
  kafka:
    producer:
      transaction-id-prefix: orders-tx-
```

Then wire the transaction manager:

```java
@Bean
KafkaTransactionManager<String, Object> kafkaTransactionManager(ProducerFactory<String, Object> pf) {
    return new KafkaTransactionManager<>(pf);
}
```

## Transactional producer shape

```java
@Transactional
void publishAll(List<OrderEvent> events) {
    events.forEach(event -> kafkaTemplate.send("orders", event.id(), event));
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Atomic consume-and-produce workflow | Kafka transactions |
| Simple producer or listener application | stay on the ordinary path |
