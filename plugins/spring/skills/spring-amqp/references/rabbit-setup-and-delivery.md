# Rabbit setup and delivery

## Dependency baseline

Before recommending a new version, check each selected Maven artifact on Maven Central for its latest stable release compatible with the project's Boot and Framework lines.
Keep project BOM or version-catalog management and existing pins unless the task authorizes changing them.

Use the Boot starter for application code and the Rabbit test module for listener and broker-focused tests.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-amqp</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.amqp</groupId>
        <artifactId>spring-rabbit-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Feature-to-artifact map

| Need | Artifact |
| --- | --- |
| Ordinary publisher and listener path | `spring-boot-starter-amqp` |
| Listener and broker-focused tests | `spring-rabbit-test` |
| RabbitMQ stream semantics | stream client support from [references/stream-variants.md](stream-variants.md) |
| Generic AMQP 1.0 protocol | `spring-amqp-client` (see [references/generic-amqp10-support.md](generic-amqp10-support.md)) |

## First safe commands

```sh
./mvnw test -Dtest=OrderMessagingTests
```

```sh
./gradlew test --tests OrderMessagingTests
```

## Build and run path

```sh
./mvnw spring-boot:run
```

```sh
./gradlew bootRun
```

Run only the application path needed to verify the task, using an authorized local broker.
Choose confirms, request-reply, streams, or multiple brokers when the delivery contract requires them.

## AMQP topology basics

Start with explicit queue, exchange, binding, and routing-key names.
Keep the topology stable before publishers and consumers are deployed.

```java
@Bean
Queue ordersQueue() {
    return QueueBuilder.durable("orders")
        .deadLetterExchange("orders.dlx")
        .deadLetterRoutingKey("orders.dlq")
        .build();
}

@Bean
DirectExchange ordersExchange() {
    return new DirectExchange("orders.exchange");
}

@Bean
Binding ordersBinding() {
    return BindingBuilder.bind(ordersQueue()).to(ordersExchange()).with("orders.created");
}
```

Use Spring declarations as the shared application contract.
Treat queue names, exchange type, and routing keys as stable integration identifiers.
Open [references/broker-configuration-and-vhost-setup.md](broker-configuration-and-vhost-setup.md) when broker credentials or vhost setup are the blocker.
Open [references/delayed-exchange-and-broker-events.md](delayed-exchange-and-broker-events.md) when delayed delivery semantics or broker-side events are the blocker.

## Message conversion basics

Use one converter strategy per module unless interoperability requirements force otherwise.

```java
@Bean
JacksonJsonMessageConverter jsonConverter() {
    return new JacksonJsonMessageConverter("com.example.orders");
}
```

The no-arg constructor trusts only `java.util` and `java.lang` by default.
Provide the package of your application payload types as the trusted package argument.

The ordinary path is one JSON payload type per message contract, with the listener receiving the already-converted domain payload.
Open [references/conversion-and-listener-method-signatures.md](conversion-and-listener-method-signatures.md) when the blocker is listener argument design, header access, validation, or custom conversion behavior.

## Publish and consume

Use `RabbitTemplate` for send and receive paths, and `@RabbitListener` for the ordinary consumer path.

```java
@Service
class OrderPublisher {
    private final RabbitTemplate rabbitTemplate;

    OrderPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    void publish(OrderCreated event) {
        rabbitTemplate.convertAndSend("orders.exchange", "orders.created", event);
    }
}

@Component
class OrderListener {
    private final OrderService orderService;

    OrderListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = "orders")
    void handle(OrderCreated event) {
        orderService.process(event);
    }
}
```

- Keep the listener payload type aligned with the converter strategy.
- Keep listeners narrow and idempotent because redelivery can happen.
- Use pull-style `RabbitTemplate` receive operations only when the consumer contract is actually synchronous.

Open [references/polling-receive.md](polling-receive.md) when the caller needs scheduled or command-driven pull semantics.

Open [references/request-reply.md](request-reply.md) when the caller truly needs synchronous broker-mediated reply semantics.

Open [references/publisher-confirms-returns-and-send-reliability.md](publisher-confirms-returns-and-send-reliability.md) when the producer must detect unroutable messages, broker acceptance, or other send-side delivery uncertainty.

### Confirms and returns baseline

Use confirms and returns only when the producer contract must distinguish broker acceptance from unroutable delivery.

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true
```

```java
@Bean
RabbitTemplateCustomizer rabbitTemplateCustomizer() {
    return template -> {
        template.setConfirmCallback((correlationData, ack, cause) -> {
        });
        template.setReturnsCallback(returned -> {
        });
    };
}
```

- Confirms answer 'did the broker accept the publish to the exchange?'
- They do not prove queue routing.
- Returns answer 'did a mandatory message remain unroutable?'
- They do not replace confirms.
- A returned message can still have a positive confirm because the broker accepted it at the exchange.
- Stay on the ordinary publish path unless the producer must react to that uncertainty explicitly.

### Reply timeout baseline

Keep request-reply timeout explicit whenever the caller truly needs synchronous broker-mediated reply semantics.

```java
rabbitTemplate.setReplyTimeout(5000);
```

Open [references/request-reply.md](request-reply.md) when the producer-consumer contract is request-reply rather than an event flow.

## Listener container baseline

Start with one baseline container factory and keep advanced concurrency or prefetch tuning out of the common path.

```java
@Bean
SimpleRabbitListenerContainerFactory ordersListenerContainerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    configurer.configure(factory, connectionFactory);
    return factory;
}
```

Use a dedicated factory only when a listener truly needs different acknowledgment, retry, or concurrency rules.
Open [references/container-variants-and-concurrency.md](container-variants-and-concurrency.md) when default listener-container behavior is not enough and the blocker is container choice, prefetch, concurrency, or ordering tradeoffs.

### Observability baseline

Wire the first production signals before tuning concurrency or retry behavior.

- Record listener duration.
- Record failure counts by listener or queue.
- Record the dead-letter path or exhausted-message count.

Open [references/listener-metrics-and-micrometer.md](listener-metrics-and-micrometer.md) when the blocker is Micrometer metrics.
Open [references/distributed-tracing-for-amqp.md](distributed-tracing-for-amqp.md) when publish and consume traces must correlate.
Open [references/delivery-debugging-checklist.md](delivery-debugging-checklist.md) when delivery diagnosis is the blocker.

## Retry and dead-letter baseline

Choose one retry policy per consumer path and make the exhausted-message outcome explicit.

```java
RetryOperationsInterceptor interceptor = RetryInterceptorBuilder.stateless()
    .maxAttempts(3)
    .recoverer(new RejectAndDontRequeueRecoverer())
    .build();
```

For the common path:

1. Start with stateless retry for idempotent listeners.
2. Send exhausted messages to an explicit DLQ or DLX path.
3. Distinguish broker failures from business-handling failures in logs and metrics.

Open [references/retry-recovery-and-transactions.md](retry-recovery-and-transactions.md) when the blocker is transactional retry, recoverer choice, or deeper failure classification.
