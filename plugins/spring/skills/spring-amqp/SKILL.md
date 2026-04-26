---
name: "spring-amqp"
description: "Build RabbitMQ or AMQP producers and consumers in Spring with RabbitTemplate, @RabbitListener, queue and exchange topology, message conversion, retry, and dead-letter handling. Use this skill when building RabbitMQ or AMQP producers and consumers in Spring with RabbitTemplate, @RabbitListener, queue and exchange topology, message conversion, listener containers, batching, retry, dead-letter handling, broker configuration, testing, observability, or stream and multi-broker variants."
metadata:
  title: "Spring AMQP"
  official_project_url: "https://spring.io/projects/spring-amqp"
  reference_doc_urls:
    - "https://docs.spring.io/spring-amqp/reference/index.html"
    - "https://docs.spring.io/spring-amqp/reference/amqp/template.html"
    - "https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages/async-annotation-driven.html"
    - "https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages/async-annotation-driven/container-management.html"
    - "https://docs.spring.io/spring-amqp/reference/amqp/resilience-recovering-from-errors-and-broker-failures.html"
    - "https://docs.spring.io/spring-amqp/reference/amqp/request-reply.html"
  version: "4.0.3"
---

Use this skill when building RabbitMQ or AMQP producers and consumers in Spring with `RabbitTemplate`, `@RabbitListener`, queue and exchange topology, message conversion, listener containers, batching, retry, dead-letter handling, broker configuration, testing, observability, or stream and multi-broker variants.

The latest released Spring AMQP line is 4.0.3. Keep this skill on the 4.0.x stable line unless the project is intentionally evaluating the 4.1 preview line.

## Boundaries

Use `spring-amqp` for RabbitMQ-oriented producers, consumers, listener containers, queue and exchange topology, delivery retries, dead-letter flows, and RabbitMQ-specific operational seams.

- Kafka or Pulsar semantics and client APIs are outside this skill's scope.
- Keep business logic outside listeners. Listeners should deserialize, validate, and delegate.
- Keep queue, exchange, routing-key, and delivery-policy names stable once publishers and consumers are deployed.

## Official surface map

Use this map to keep the official Spring AMQP 4.x surface visible without pushing the common queue path into `references/`.

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| Queue and exchange topology | The module needs explicit queue, exchange, binding, and routing-key contracts | Broker credentials or vhost setup are the blocker in [references/broker-configuration-and-vhost-setup.md](references/broker-configuration-and-vhost-setup.md), or delayed exchange and broker events are the blocker in [references/delayed-exchange-and-broker-events.md](references/delayed-exchange-and-broker-events.md) |
| `RabbitTemplate` send and receive | The module publishes or performs occasional pull-style receive operations | Request-reply semantics are the blocker in [references/request-reply.md](references/request-reply.md), or pull-style polling receive is the blocker in [references/polling-receive.md](references/polling-receive.md) |
| Publish reliability | The producer must know whether a message was routed and accepted | Publisher confirms, returns, or mandatory publishing are the blocker in [references/publisher-confirms-returns-and-send-reliability.md](references/publisher-confirms-returns-and-send-reliability.md) |
| `@RabbitListener` consumer path | The consumer is an ordinary queue listener with one payload contract | Listener signatures, headers, validation, or conversion are the blocker in [references/conversion-and-listener-method-signatures.md](references/conversion-and-listener-method-signatures.md) |
| Listener containers | One baseline container factory is enough for the first consumer | Container choice, prefetch, concurrency, or ordering tradeoffs are the blocker in [references/container-variants-and-concurrency.md](references/container-variants-and-concurrency.md) |
| Retry, recovery, and transactions | The listener needs one explicit exhausted-message outcome | Recoverer choice, transactional semantics, or deeper failure classification are the blocker in [references/retry-recovery-and-transactions.md](references/retry-recovery-and-transactions.md) |
| Batching and async listeners | The consumer path is no longer one-message-in, one-message-out | Batch listeners are the blocker in [references/batch-listeners.md](references/batch-listeners.md), async returns are the blocker in [references/async-return-listeners.md](references/async-return-listeners.md), polling receive is the blocker in [references/polling-receive.md](references/polling-receive.md), or consumer threading is the blocker in [references/listener-threading-and-back-pressure.md](references/listener-threading-and-back-pressure.md) |
| Streams | Queue semantics are not enough | RabbitMQ stream plugin semantics are the blocker in [references/stream-variants.md](references/stream-variants.md) |
| Multiple brokers | One broker connection is no longer enough | Broker isolation is the blocker in [references/multi-broker-variants.md](references/multi-broker-variants.md) |
| Testing | The contract needs broker-focused or listener-focused verification | Test harness depth is the blocker in [references/testing-support-and-listener-harnesses.md](references/testing-support-and-listener-harnesses.md) |
| Observability and debugging | Delivery behavior must be measured or diagnosed in production | Listener metrics are the blocker in [references/listener-metrics-and-micrometer.md](references/listener-metrics-and-micrometer.md), tracing is the blocker in [references/distributed-tracing-for-amqp.md](references/distributed-tracing-for-amqp.md), or delivery diagnosis is the blocker in [references/delivery-debugging-checklist.md](references/delivery-debugging-checklist.md) |

## Common path

The ordinary Spring AMQP job is:

1. Fix queue, exchange, binding, and routing-key names first.
2. Add the AMQP starter and choose one message format, usually JSON, for the module.
3. Declare the broker topology in Spring so publisher and consumer code share the same contract.
4. Publish through `RabbitTemplate` and consume through `@RabbitListener`.
5. Set one baseline listener container configuration and one retry or dead-letter policy before production rollout.
6. Prove the payload shape, delivery path, and one representative failure path with tests.
7. Add batching, request-reply, streams, or multiple brokers only when the ordinary queue path is already correct.

## Dependency baseline

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
| RabbitMQ stream semantics | stream client support from [references/stream-variants.md](references/stream-variants.md) |

## First safe commands

```bash
./mvnw test -Dtest=OrderMessagingTests
```

```bash
./gradlew test --tests OrderMessagingTests
```

## Build and run path

```bash
./mvnw spring-boot:run
```

```bash
./gradlew bootRun
```

Run the ordinary queue path first. Add broker confirms, request-reply, streams, or multiple brokers only after the base send/receive flow is stable.

## AMQP topology basics

Start with explicit queue, exchange, binding, and routing-key names. Keep the topology stable before publishers and consumers are deployed.

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

Use Spring declarations as the shared application contract. Treat queue names, exchange type, and routing keys as stable integration identifiers. Open [references/broker-configuration-and-vhost-setup.md](references/broker-configuration-and-vhost-setup.md) when broker credentials or vhost setup are the blocker. Open [references/delayed-exchange-and-broker-events.md](references/delayed-exchange-and-broker-events.md) when delayed delivery semantics or broker-side events are the blocker.

## Message conversion basics

Use one converter strategy per module unless interoperability requirements force otherwise.

```java
@Bean
JacksonJsonMessageConverter jsonConverter() {
    return new JacksonJsonMessageConverter();
}
```

The ordinary path is one JSON payload type per message contract, with the listener receiving the already-converted domain payload. Open [references/conversion-and-listener-method-signatures.md](references/conversion-and-listener-method-signatures.md) when the blocker is listener argument design, header access, validation, or custom conversion behavior.

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

Open [references/polling-receive.md](references/polling-receive.md) when the caller needs scheduled or command-driven pull semantics.

Open [references/request-reply.md](references/request-reply.md) when the caller truly needs synchronous broker-mediated reply semantics.

Open [references/publisher-confirms-returns-and-send-reliability.md](references/publisher-confirms-returns-and-send-reliability.md) when the producer must detect unroutable messages, broker acceptance, or other send-side delivery uncertainty.

### Confirms and returns baseline

Use confirms and returns only when the producer contract must distinguish broker acceptance from publish attempt and unroutable delivery.

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
RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setConfirmCallback((correlationData, ack, cause) -> {
    });
    template.setReturnsCallback(returned -> {
    });
    return template;
}
```

- Confirms answer 'did the broker accept the publish request?'
- Returns answer 'did the exchange fail to route the message to any queue?'
- Stay on the ordinary publish path unless the producer must react to that uncertainty explicitly.

### Reply timeout baseline

Keep request-reply timeout explicit whenever the caller truly needs synchronous broker-mediated reply semantics.

```java
rabbitTemplate.setReplyTimeout(5000);
```

Open [references/request-reply.md](references/request-reply.md) when the producer-consumer contract is genuinely request-reply rather than an event flow.

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

Use a dedicated factory only when a listener truly needs different acknowledgment, retry, or concurrency rules. Open [references/container-variants-and-concurrency.md](references/container-variants-and-concurrency.md) when default listener-container behavior is not enough and the blocker is container choice, prefetch, concurrency, or ordering tradeoffs.

### Observability baseline

Wire the first production signals before tuning concurrency or retry behavior.

- Record listener duration.
- Record failure counts by listener or queue.
- Record the dead-letter path or exhausted-message count.

Open [references/listener-metrics-and-micrometer.md](references/listener-metrics-and-micrometer.md) when the blocker is Micrometer metrics. Open [references/distributed-tracing-for-amqp.md](references/distributed-tracing-for-amqp.md) when publish and consume traces must correlate. Open [references/delivery-debugging-checklist.md](references/delivery-debugging-checklist.md) when delivery diagnosis is the blocker.

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

Open [references/retry-recovery-and-transactions.md](references/retry-recovery-and-transactions.md) when the blocker is transactional retry, recoverer choice, or deeper failure classification.

## Minimal testing shape

Verify the first Spring AMQP path with one delivery test and one failure-path test.

```java
@SpringRabbitTest
class OrderMessagingTests {
}
```

- Verify published messages reach the expected exchange, routing key, and queue.
- Verify the listener receives the deserialized payload shape the publisher sends.
- Verify retry and dead-letter behavior on one representative failure path.
- Verify converter configuration matches the payload format used in production.

Open [references/testing-support-and-listener-harnesses.md](references/testing-support-and-listener-harnesses.md) when the task needs listener-test harnesses, broker-backed integration depth, or repeatable contract checks.

## Failure classification

- Treat message conversion, payload validation, and listener-signature mismatches as contract failures.
- Treat broker disconnects, transient downstream outages, and resource exhaustion as infrastructure failures that may justify retry.
- Treat exhausted retries, rejected business operations, and poison messages as dead-letter or recovery-path failures that must stay observable.

## Output and configuration shapes

### Queue and exchange naming shape

```text
orders
orders.exchange
orders.created
orders.dlq
```

### Publish shape

```java
rabbitTemplate.convertAndSend("orders.exchange", "orders.created", event);
```

### Listener shape

```java
@RabbitListener(queues = "orders")
void handle(OrderCreated event) {
    orderService.process(event);
}
```

### Container factory shape

```java
SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
```

## Output contract

Return:

1. The queue, exchange, binding, and routing-key contract
2. The publish and consume shape, including converter strategy
3. The chosen listener container and retry or dead-letter policy
4. The focused test shape proving one delivery path and one failure path
5. Any blocker that requires request-reply, streams, batching, async returns, or multiple brokers

## Production guardrails

- Keep topology names stable after publishers and consumers are deployed.
- Make listeners idempotent because duplicate delivery and redelivery can occur.
- Bound concurrency only after checking ordering requirements and downstream capacity.
- Keep dead-letter queues observable and drainable.
- Make broker credentials, virtual hosts, and listener tuning explicit configuration, not hidden defaults.

## References

- Open [references/broker-configuration-and-vhost-setup.md](references/broker-configuration-and-vhost-setup.md) when the blocker is broker credentials, virtual-host setup, or explicit connection settings.
- Open [references/delayed-exchange-and-broker-events.md](references/delayed-exchange-and-broker-events.md) when the blocker is delayed delivery semantics or broker-side operational events.
- Open [references/publisher-confirms-returns-and-send-reliability.md](references/publisher-confirms-returns-and-send-reliability.md) when the blocker is publisher confirms, returns, mandatory publishing, or send-side delivery guarantees.
- Open [references/container-variants-and-concurrency.md](references/container-variants-and-concurrency.md) when the blocker is container choice, dedicated factories, prefetch, concurrency, or ordering tradeoffs.
- Open [references/conversion-and-listener-method-signatures.md](references/conversion-and-listener-method-signatures.md) when the blocker is payload conversion, listener arguments, headers, validation, or custom method signatures.
- Open [references/retry-recovery-and-transactions.md](references/retry-recovery-and-transactions.md) when the blocker is retry internals, recoverers, transactions, or deeper failure classification.
- Open [references/request-reply.md](references/request-reply.md) when the blocker is synchronous request-reply over RabbitMQ.
- Open [references/batch-listeners.md](references/batch-listeners.md) when the blocker is whole-batch consumption.
- Open [references/async-return-listeners.md](references/async-return-listeners.md) when the blocker is asynchronous listener return handling.
- Open [references/polling-receive.md](references/polling-receive.md) when the blocker is pull-style receive.
- Open [references/listener-threading-and-back-pressure.md](references/listener-threading-and-back-pressure.md) when the blocker is consumer threading or back pressure.
- Open [references/stream-variants.md](references/stream-variants.md) when the ordinary queue consumer path is not enough and the task needs RabbitMQ stream semantics.
- Open [references/multi-broker-variants.md](references/multi-broker-variants.md) when the blocker is isolating multiple broker connections, templates, or listener factories.
- Open [references/testing-support-and-listener-harnesses.md](references/testing-support-and-listener-harnesses.md) when the task needs listener harnesses, broker-backed integration tests, or contract verification.
- Open [references/listener-metrics-and-micrometer.md](references/listener-metrics-and-micrometer.md) when the blocker is listener metrics or Micrometer wiring.
- Open [references/distributed-tracing-for-amqp.md](references/distributed-tracing-for-amqp.md) when the blocker is end-to-end tracing for publish and consume paths.
- Open [references/delivery-debugging-checklist.md](references/delivery-debugging-checklist.md) when the blocker is connection debugging or delivery diagnosis.
