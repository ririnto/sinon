# Integration implementation examples

## Gateway and flow shape

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

## Service activator annotation shape

```java
@ServiceActivator(inputChannel = "orders.validated")
void process(OrderCommand command) {
    service.process(command);
}
```

Use this annotation shape only when the surrounding application already standardized on annotation-driven endpoint wiring.
For new flows, keep the DSL-first `handle(...)` style as the default.

## Splitter and aggregator shape

```java
IntegrationFlow.from("orders.batches")
    .split()
    .channel("orders.parts")
    .aggregate(aggregator -> aggregator.correlationStrategy(message -> message.getHeaders().get("batchId")).releaseStrategy(group -> group.size() >= 10))
```

## Poller shape

```java
Pollers.fixedDelay(Duration.ofSeconds(5)).maxMessagesPerPoll(10)
```

## Router shape

```java
.route(OrderCommand::priority, mapping -> mapping.channelMapping(Priority.HIGH.name(), "orders.priority").channelMapping(Priority.NORMAL.name(), "orders.standard"))
```
