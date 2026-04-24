---
name: "spring-integration"
description: "Build message-driven application flows with Spring Integration channels, routers, filters, splitters, aggregators, gateways, pollers, and protocol adapters. Use this skill when building message-driven application flows with Spring Integration using channels, routers, filters, splitters, aggregators, gateways, pollers, and protocol adapters."
metadata:
  title: "Spring Integration"
  official_project_url: "https://spring.io/projects/spring-integration"
  reference_doc_urls:
    - "https://docs.spring.io/spring-integration/reference/"
    - "https://docs.spring.io/spring-integration/reference/core.html"
    - "https://docs.spring.io/spring-integration/reference/router.html"
    - "https://docs.spring.io/spring-integration/reference/dsl.html"
    - "https://docs.spring.io/spring-integration/reference/system-management.html"
    - "https://docs.spring.io/spring-integration/reference/file.html"
    - "https://docs.spring.io/spring-integration/reference/http.html"
    - "https://docs.spring.io/spring-integration/reference/jdbc.html"
    - "https://docs.spring.io/spring-integration/reference/testing.html"
  version: "7.0.4"
---

Use this skill when building message-driven application flows with Spring Integration using channels, routers, filters, splitters, aggregators, gateways, pollers, and protocol adapters.

## Boundaries

Use `spring-integration` for Enterprise Integration Patterns inside or at the edge of a Spring application.

- Use `spring-kafka`, `spring-amqp`, or `spring-pulsar` when you only need direct broker APIs without an Integration flow.
- Use `spring-cloud` for distributed-system infrastructure such as Config, Gateway, or general service-to-service wiring.
- Keep domain logic out of the flow graph. Flows should orchestrate message movement, routing, and adaptation.

## Common path

The ordinary Spring Integration job is:

1. Draw the flow as source -> channel -> endpoint -> channel -> sink before coding.
2. Choose the smallest set of EIP components that expresses the routing and transformation need.
3. Use gateways for request-reply boundaries and adapters for one-way boundaries, whether the caller is application code or an external system.
4. Choose channel semantics, poller behavior, and error-channel routing before the flow goes live.
5. Put idempotency, retries, transactions, and persistent stores only where restart or remote failure semantics require them.
6. Add one graph-level test that proves the intended message path and one failure-path test that proves error routing.

## Core flow decisions

| Situation                                              | Use                       |
| --- | --- |
| One caller hands off to one handler inline             | `DirectChannel`           |
| The flow needs buffering between producer and consumer | `QueueChannel`            |
| One message fans out to several subscribers            | publish-subscribe channel |
| Application code sends into the flow                   | messaging gateway         |
| External system sends one-way events                   | inbound adapter           |
| External system expects request-reply behavior         | inbound gateway           |

Choose the simplest channel and endpoint pair that matches the delivery semantics. Add pollers only for sources that do not naturally push messages.

## Dependency baseline

Use the Boot starter for core Integration features and add only the protocol modules the flow actually needs.

For the current stable line, Spring Integration is 7.0.4. The latest released Spring Boot line, 4.0.5, already manages Spring Integration 7.0.4. Older Boot 3.5.x applications still manage Spring Integration 6.5.8 and therefore remain a separate compatibility branch.

### Core baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-integration</artifactId>
    </dependency>
</dependencies>
```

### Optional adapter and test modules

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.integration</groupId>
        <artifactId>spring-integration-file</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.integration</groupId>
        <artifactId>spring-integration-http</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.integration</groupId>
        <artifactId>spring-integration-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Remove any adapter or test module the flow does not actually use.

When Spring Boot already manages the module line, keep Spring Integration artifacts versionless. Add an exact version only on a standalone path that intentionally pins Spring Integration outside Boot-managed dependency control.

## First safe configuration

### Gateway shape

```java
@MessagingGateway(defaultRequestChannel = "orders.input")
interface OrderGateway {
    void submit(OrderCommand command);
}
```

### Basic flow shape

```java
@Bean
IntegrationFlow orderFlow(OrderValidator validator, OrderService service) {
    return IntegrationFlow.from("orders.input")
        .transform(OrderCommand.class, command -> command.normalize())
        .filter(OrderCommand.class, validator::isValid)
        .handle(service, "process")
        .get();
}
```

This ordinary DSL chain covers the three most common endpoint types in one place: transformer, filter, and service activator.

### Error channel shape

```java
@Bean
IntegrationFlow integrationErrors() {
    return IntegrationFlow.from("errorChannel")
        .handle(message -> logger.warn("integration failure", message.getPayload()))
        .get();
}
```

Use the default `errorChannel` for the ordinary baseline. Open [references/error-handling-and-retry-patterns.md](references/error-handling-and-retry-patterns.md) when the flow needs endpoint advice, retry, circuit breaking, or explicit custom error-channel routing.

## Coding procedure

1. Choose channel semantics deliberately: direct, queued, publish-subscribe, or reactive.
2. Use filters, routers, splitters, and aggregators only when the flow actually needs them.
3. Keep message headers intentional and stable when downstream routing depends on them.
4. Add persistent stores only when idempotency, aggregation, resequencing, or poller state must survive restarts.
5. Use Java DSL for new code unless the surrounding application already standardized on another style.
6. Add runtime flow registration, reactive channels, or adapter-specific modules only when the ordinary direct flow is not enough.
7. Test both the happy path and one representative error or discard path.

## Basic DSL verbs

| Need | DSL shape |
| --- | --- |
| Change the payload | `.transform(...)` |
| Drop or reroute some messages | `.filter(...)` |
| Choose the next channel from content or headers | `.route(...)` |
| Invoke application code at the endpoint boundary | `.handle(...)` |
| Fan one message out into parts | `.split(...)` |
| Rejoin related messages | `.aggregate(...)` |

## Endpoint composition rules

- Use a transformer when the payload shape changes.
- Use a filter when the flow should discard or reroute some messages.
- Use a router when the next channel depends on message content or headers.
- Use a service activator when application code should consume or reply to the current message at the endpoint boundary.
- Use a splitter and aggregator pair only when one message really must fan out and rejoin.

When a flow fans out and rejoins, define correlation, release, and timeout rules deliberately instead of assuming the default group behavior is correct for the business boundary.

## Implementation examples

### Gateway and flow shape

```java
@MessagingGateway(defaultRequestChannel = "orders.input")
interface OrderGateway {
    void submit(OrderCommand command);
}

@Bean
IntegrationFlow orderFlow(OrderValidator validator, OrderService service) {
    return IntegrationFlow.from("orders.input")
        .transform(OrderCommand.class, command -> command.normalize())
        .filter(OrderCommand.class, validator::isValid)
        .handle(service, "process")
        .get();
}
```

### Service activator annotation shape

```java
@ServiceActivator(inputChannel = "orders.validated")
void process(OrderCommand command) {
    service.process(command);
}
```

Use this annotation shape only when the surrounding application already standardized on annotation-driven endpoint wiring. For new flows, keep the DSL-first `handle(...)` style as the default.

### Splitter and aggregator shape

```java
IntegrationFlow.from("orders.batches")
    .split()
    .channel("orders.parts")
    .aggregate(aggregator -> aggregator
        .correlationStrategy(message -> message.getHeaders().get("batchId"))
        .releaseStrategy(group -> group.size() >= 10))
```

### Poller shape

```java
Pollers.fixedDelay(Duration.ofSeconds(5)).maxMessagesPerPoll(10)
```

### Router shape

```java
.route(OrderCommand::priority, mapping -> mapping
    .channelMapping(Priority.HIGH.name(), "orders.priority")
    .channelMapping(Priority.NORMAL.name(), "orders.standard"))
```

## Output and configuration shapes

Return these artifacts for the ordinary path:

1. One gateway or explicit entry channel
2. One IntegrationFlow configuration class
3. One adapter or transport boundary configuration when the flow leaves the process
4. One graph-level test plus one failure-path test

### Flow sketch shape

```text
source -> channel -> endpoint -> channel -> sink
```

### Gateway channel shape

```text
orders.input
```

### Poller shape

```java
Pollers.fixedDelay(Duration.ofSeconds(5))
```

## Testing checklist

- Verify the intended message path through the graph.
- Verify discard, error-channel, or retry behavior on one representative failure path.
- Verify channel semantics match ordering and throughput expectations.
- Verify headers used for routing or correlation are actually present.
- Verify persistent stores are present only when restart or clustering semantics require them.
- Verify poller, retry, and endpoint advice configuration match the intended failure boundary.

## Production checklist

- Keep channel names, endpoint ids, and header conventions stable.
- Bound queues or use backpressure-aware channels where accumulation can grow.
- Make external adapters idempotent before adding retries.
- Put explicit timeouts on remote adapters and pollers.
- Keep error channels, retry advice, and circuit-breaker behavior observable.
- Treat flow tests and adapter contracts as part of the operational compatibility surface.

## References

- Open [references/error-handling-and-retry-patterns.md](references/error-handling-and-retry-patterns.md) when the flow needs endpoint advice, retry, circuit breaking, or explicit error-channel routing.
- Open [references/testing-integration-flows.md](references/testing-integration-flows.md) when the task needs `@SpringIntegrationTest`, `MockIntegrationContext`, `noAutoStartup`, or graph-level assertions.
- Open [references/pollers-transactions-and-stores.md](references/pollers-transactions-and-stores.md) when the flow depends on pollers, transactional sources, aggregators, or persistent message stores.
- Open [references/dsl-runtime-flows-and-reactive.md](references/dsl-runtime-flows-and-reactive.md) when the task needs runtime flow registration, subflows, reactive channels, or composed Java DSL flows.
- Open [references/control-bus-and-system-management.md](references/control-bus-and-system-management.md) when the application must inspect, start, stop, or observe Integration endpoints in production.
- Open [references/adapter-family-selection.md](references/adapter-family-selection.md) when choosing protocol adapters or module boundaries for a concrete external system.
- Open [references/native-aot-support.md](references/native-aot-support.md) when the flow must run in a native image and adapter or reflection constraints become part of the design.
