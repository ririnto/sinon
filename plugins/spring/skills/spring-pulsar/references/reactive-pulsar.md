# Spring Pulsar reactive support

Open this reference when the application uses the reactive Pulsar programming model instead of imperative templates and listeners.

## Reactive setup delta

Add the reactive Spring Pulsar starter before adopting the APIs below. For Spring Boot 4.x applications, use `org.springframework.boot:spring-boot-starter-pulsar-reactive`. For non-Boot setups, import the Spring Pulsar BOM and add the reactive module directly. Verify that the selected Spring Pulsar line still provides reactive support before adopting this branch. Do not assume the imperative baseline alone enables the reactive branch.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-pulsar-reactive</artifactId>
</dependency>
```

## Reactive producer shape

```java
Flux<MessageId> publish(Flux<ShipmentEvent> events) {
    return reactivePulsarTemplate.send("shipments", events);
}
```

## Reactive consumer shape

```java
@ReactivePulsarListener(topics = "shipments", subscriptionName = "warehouse")
Mono<Void> handle(ShipmentEvent event) {
    return service.handle(event);
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Ordinary service uses imperative send and listener methods | stay on the common path |
| End-to-end pipeline is already reactive | reactive Pulsar |
| Reader replay or audit traversal is required | reader reference |

## Gotchas

- Do not mix imperative and reactive listeners on the same topic unless the delivery model is explicitly designed for both.
- Do not adopt the reactive branch just to wrap one blocking service call in `Mono` or `Flux`.
