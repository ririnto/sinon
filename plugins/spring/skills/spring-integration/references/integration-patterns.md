---
title: Spring Integration Patterns Reference
description: >-
  Reference for Spring Integration flow shape, channel decisions, and routing patterns.
---

Use this reference when the integration problem is already message-oriented and the remaining work is shaping the flow correctly.

## Flow Shape

- inbound adapter
- transform or route
- service activator or outbound adapter

## Channel Examples

Channels connect the flow segments; name them by their role in the data path:

```java
@Configuration
class IntegrationConfig {

    @Bean
    MessageChannel ordersChannel() {
        return MessageChannels.direct("orders").get();
    }

    @Bean
    MessageChannel fulfilledChannel() {
        return MessageChannels.direct("fulfilled").get();
    }

    @Bean
    MessageChannel deadLetterChannel() {
        return MessageChannels.direct("orders.dlq").get();
    }
}
```

## Router vs Filter Examples

```java
// Router: route to different channels based on payload type
@Bean
IntegrationFlow orderRoutingFlow() {
    return IntegrationFlow.from("ordersChannel")
            .routeByRecipient(r -> r
                    .recipient("shipChannel", m -> m.getPayload() instanceof ShipOrder)
                    .recipient("returnChannel", m -> m.getPayload() instanceof ReturnOrder)
                    .defaultOutput("unknownOrders"))
            .get();
}

// Filter: pass only if condition is met, drop otherwise
@Bean
IntegrationFlow validatedFlow() {
    return IntegrationFlow.from("inboundChannel")
            .filter(Order.class, order -> order.isValid(),
                    f -> f.discardChannel("rejectedChannel"))
            .get();
}
```

## Error Channel Example

Route exceptions explicitly so operators can observe failed messages:

```java
@Bean
IntegrationFlow withErrorChannel(MessageChannel ordersChannel) {
    return IntegrationFlow.from(ordersChannel)
            .<Order>handle((order, headers) -> {
                // work that may throw
                return orderProcessor.process(order);
            })
            .get();
}

@Bean
MessageChannel ordersErrorChannel() {
    return MessageChannels.direct("orders.error").get();
}

@Bean
IntegrationFlow errorHandlingFlow() {
    return IntegrationFlow.from("orders.error")
            .log(LoggingHandler.Level.ERROR, "Failed order message")
            .handle(MessageConsumer.class, msg -> {
                // move to DLQ, alert, or compensating logic
            })
            .get();
}
```

## Message Store Example

Use a persistent message store only when restart survival or durable buffering is required:

```java
@Bean
JdbcMessageStore jdbcMessageStore(DataSource dataSource) {
    return new JdbcMessageStore(dataSource);
}

@Bean
MessageChannel durableInputChannel(JdbcMessageStore jdbcMessageStore) {
    return MessageChannels.queue("durableInput", 100)
            .durability(true)
            .messageStore(jdbcMessageStore)
            .get();
}
```

For simple in-memory buffering with no restart survival need, omit `messageStore` entirely; the channel remains in-memory.

## Error Channel Rule

Make failure routing explicit when the integration flow should not simply fail in place.

- use an error channel when operators or downstream handlers need to see failed messages explicitly
- do not hide retry or dead-letter semantics inside scattered service activators

## Message Store Rule

Use a persistent message store only when restart survival or durable buffering is truly required.

- keep the durability need explicit
- do not add persistence to every flow just because asynchronous processing exists

## Transaction Boundary Rule

If a flow crosses database writes, Kafka publishing, or other side effects, make the Spring transaction and post-commit policy explicit rather than assuming the messaging flow is atomic on its own.

## Common Pitfalls

- leaking transport details into the middle of the flow
- hiding transformation logic inside unrelated services
- overcomplicating small integrations with too many intermediate channels
