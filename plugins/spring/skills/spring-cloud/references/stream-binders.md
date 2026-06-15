# Spring Cloud Stream binders

Open this reference when the ordinary config-gateway-client path in `SKILL.md` is not enough and the task is specifically about Spring Cloud Stream binder wiring.

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-kafka</artifactId>
</dependency>
```

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-rabbit</artifactId>
</dependency>
```

Choose the binder that matches the real broker.

## Kafka Streams binder (2025.1.x)

Spring Cloud Stream 5.0 adds per-binding KTable materialization controls for cache and logging in the Kafka Streams binder.
Consumer priority ordering is also supported to control which consumers receive messages first.

## Kafka binder multiple bindings fix (5.0.2)

Kafka Binder did not work correctly with multiple bindings pointing to different topics in the same application.
This is fixed in 5.0.2. Verify multi-binding topologies against the updated binder.

## MessageRoutingCallback

Route messages to specific handler methods based on message content by implementing `MessageRoutingCallback` and registering it under the `Event Routing` configuration section.

## Function handler integration in Gateway (2025.0.x)

Spring Cloud Gateway 4.3.x supports `spring-cloud-function` and `spring-cloud-stream` handlers as route targets.
This lets gateway routes invoke functions directly instead of proxying to a downstream service.

## Gotchas

- Do not mix binder-specific behavior into a generic service contract casually.
- Do not add several binders unless the application truly speaks to several brokers.
- In 2025.1.x, consumer priority and KTable materialization controls are Kafka Streams binder only.

## Validation rule

Verify the chosen binder and destination names match the real broker and topic or queue contract.
