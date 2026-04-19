# Spring Pulsar reactive support

Open this reference when the application uses the reactive Pulsar programming model instead of imperative templates and listeners.

## Reactive setup delta

Add the reactive Spring Pulsar starter before adopting the APIs below. For Spring Boot applications, use `org.springframework.boot:spring-boot-starter-pulsar-reactive`. Do not assume the imperative baseline alone enables the reactive branch.

## Reactive producer shape

```java
Flux<MessageResult<Void>> publish(Flux<ShipmentEvent> events) {
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
