# Generic AMQP 1.0 support

Open this reference when the RabbitMQ-specific path in [SKILL.md](../SKILL.md) is not needed and the blocker is generic AMQP 1.0 protocol interaction.

## AMQP 1.0 blocker

Problem: the integration peer uses AMQP 1.0 natively, or RabbitMQ-specific features are not required and a protocol-level interaction is preferred.

Solution: use the `spring-amqp-client` module with its own `@EnableAmqp` / `@AmqpListener` annotations, `AmqpClient` fluent API, and `AmqpConnectionFactory`.

The `spring-amqp-client` module is separate from the RabbitMQ-specific `spring-rabbit` module. It uses the Qpid ProtonJ2 client library and works with any AMQP 1.0 peer.

```xml
<dependency>
    <groupId>org.springframework.amqp</groupId>
    <artifactId>spring-amqp-client</artifactId>
</dependency>
```

## Connection factory

```java
@Bean
AmqpConnectionFactory connectionFactory() {
    return new SingleAmqpConnectionFactory();
}
```

Defaults to `localhost:5672` with infinite reconnection. Configure via setters (`host`, `port`, `user`) or the ProtonJ `ConnectionOptions` builder.

## AmqpClient fluent API

```java
@Bean
AmqpClient amqpClient(AmqpConnectionFactory connectionFactory) {
    return AmqpClient.builder(connectionFactory)
            .defaultToAddress("/queues/orders")
            .messageConverter(new JacksonJsonMessageConverter())
            .build();
}
```

Send and receive operations return `CompletableFuture`.

```java
amqpClient.to("/queues/orders").body(orderCreated).send();

CompletableFuture<OrderCreated> received = amqpClient.from("/queues/orders").receiveAndConvert();
```

## @AmqpListener

```java
@EnableAmqp

@AmqpListener(addresses = "orders")
void handle(OrderCreated event) {
    process(event);
}
```

Supports `Mono` return types, `CompletableFuture`, and Kotlin `suspend` functions. Request-reply with `@SendTo` fallback is supported.

## Decision points

| Situation | First choice |
| --- | --- |
| RabbitMQ-specific features needed (DLX, delayed exchange, publisher confirms) | stay on RabbitMQ-specific path |
| Peer uses AMQP 1.0 natively or RabbitMQ features are not required | `spring-amqp-client` |

## Pitfalls

- Do not mix `@AmqpListener` and `@RabbitListener` infrastructure in one module without clear separation.
- AMQP 1.0 addresses are not the same as RabbitMQ routing keys; verify the address convention for the target broker.
- The `@EnableAmqp` annotation is distinct from `@EnableRabbit`; adding both registers separate listener infrastructures.
