# Event Externalization

Open this reference when module events must be published to external brokers such as Kafka, AMQP, or JMS.

## Externalization boundary

Use event externalization when domain events must be forwarded to external systems through message brokers. Do not use it for internal cross-module events that stay within the application.

## Broker starters

| Broker | Artifact |
| --- | --- |
| Kafka | `spring-modulith-events-kafka` |
| AMQP (Rabbit) | `spring-modulith-events-amqp` |
| JMS | `spring-modulith-events-jms` |
| Spring Messaging | `spring-modulith-events-messaging` |

## Enable or disable

```properties
spring.modulith.events.externalization.enabled=true
spring.modulith.events.externalization.serialize-externalization=false
```

Set `serialize-externalization` to `true` when broker interactions must be sequential to prevent event ordering issues during resubmission spikes.

## Annotation-based externalization

```java
@Externalized("order-events::#{#this.orderId()}")
class OrderCompleted {
    String orderId() {
    }
}
```

Use `$target` for the routing target or `$target::$key` for a dynamic routing key derived from SpEL expressions.

```java
@Externalized("#{@routingKeyResolver.resolve(#this)}")
class OrderCompleted {
}
```

## Programmatic externalization configuration

```java
@Bean
EventExternalizationConfiguration eventExternalizationConfiguration() {
    return EventExternalizationConfiguration.externalizing()
        .select(EventExternalizationConfiguration.annotatedAsExternalized())
        .mapping(SomeEvent.class, event -> new ExternalizedPayload(event))
        .headers(event -> Map.of("source", "modulith"))
        .routeKey(WithKeyProperty.class, WithKeyProperty::getKey)
        .build();
}
```

- `select(...)` -- defines which events are externalized.
- `.mapping(...)` -- transforms the event payload before serialization.
- `.headers(...)` -- adds broker-specific message headers.
- `.routeKey(...)` -- extracts or computes a routing key from the event.

## Routing target defaults

When no target is specified in the annotation, Spring Modulith uses the application-local type name. For a base package `com.acme.app` and event type `com.acme.app.sample.SampleEvent`, the target is `sample.SampleEvent`.

## Kafka-specific configuration

```properties
spring.modulith.events.kafka.enable-json=true
```

## RabbitMQ-specific configuration

```properties
spring.modulith.events.rabbitmq.enable-json=true
```

## MongoDB transaction note

When using the MongoDB event publication registry starter, transactions are automatically enabled and require a replica set. Disable with:

```properties
spring.modulith.events.mongodb.transaction-management.enabled=false
```

## Event outbox

The outbox pattern reliably forwards application events to external systems. Spring Modulith integrates with Namastack Outbox and JobRunr for outbox-based event publication.

### Namastack Outbox

```xml
<dependency>
    <groupId>io.namastack</groupId>
    <artifactId>namastack-outbox</artifactId>
    <version>1.7</version>
</dependency>
```

### JobRunr

```xml
<dependency>
    <groupId>org.jobrunr</groupId>
    <artifactId>jobrunr</artifactId>
    <version>8.6.1</version>
</dependency>
```

When an outbox integration is on the classpath, events tracked by the event publication registry are forwarded through the outbox instead of direct broker publication. The outbox consumer handles the actual delivery to the external system, providing at-least-once delivery guarantees.

## Decision points

| Situation | Use |
| --- | --- |
| Domain events need to reach an external Kafka topic | `@Externalized` with Kafka starter |
| AMQP routing key must derive from event payload | SpEL expression in annotation |
| Event payload needs transformation before externalization | Programmatic `.mapping(...)` |
| Broker interactions must be sequential | `serialize-externalization=true` |
| Reliable at-least-once delivery to external systems | Event outbox (Namastack or JobRunr) |

## Verification rule

Verify one test proves the externalized event appears on the broker topic or exchange with the expected routing key and payload before wiring production listeners to it.
