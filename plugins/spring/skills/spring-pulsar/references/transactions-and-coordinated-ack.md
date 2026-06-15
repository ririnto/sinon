# Spring Pulsar transactions and coordinated acknowledgment

Open this reference when grouped writes or acknowledgment coordination are required.

## Enabling transactions

Transaction support is disabled by default.
Enable with the Boot property.

```yaml
spring:
  pulsar:
    transaction:
      enabled: true
```

## Local transaction shape

```java
pulsarTemplate.executeInTransaction(operations -> {
    operations.send("shipments", event);
    operations.send("shipment-audit", auditEvent);
    return null;
});
```

The callback receives the template instance.
All operations are enlisted in the transaction.
Normal exit commits.
Exception thrown rolls back.

## Consume-process-produce shape

When listener transactions are enabled, the container starts a transaction, invokes the listener, enlists any template sends and the acknowledgment in the same transaction, and commits.

```java
@PulsarListener(topics = "my-input-topic")
void listen(String msg) {
    var transformedMsg = msg.toUpperCase(Locale.ROOT);
    transactionalTemplate.send("my-output-topic", transformedMsg);
}
```

## Coordinated acknowledgment with PulsarTransactionManager

Use `PulsarTransactionManager` with `@Transactional` or `TransactionTemplate` for synchronized transactions.

```java
@Transactional("pulsarTransactionManager")
public void processInTransaction(ShipmentEvent event) {
    pulsarTemplate.send("shipments", event);
    pulsarTemplate.send("shipment-audit", auditEvent);
}
```

## Transaction timeout configuration

```java
@Bean
PulsarTemplateCustomizer<?> templateCustomizer() {
    return template -> template.transactions().setTimeout(Duration.ofSeconds(45));
}
```

```java
@Bean
PulsarContainerFactoryCustomizer<ConcurrentPulsarListenerContainerFactory<?>> containerCustomizer() {
    return factory -> factory.getContainerProperties().transactions().setTimeout(Duration.ofSeconds(45));
}
```

## Decision points

- Use transactions only when grouped Pulsar writes or coordinated acknowledgment semantics are truly required.
- Record listeners create a transaction per message.
  - Batch listeners create a transaction per batch.
  - Batch ack mode cannot be used with transactional record listeners.
- Transactional batch listeners do not support custom error handlers.
- Use local transactions (`executeInTransaction`) for Pulsar-only operations.
  - Use `PulsarTransactionManager` with `@Transactional` when combining Pulsar writes with other transactional resources.
