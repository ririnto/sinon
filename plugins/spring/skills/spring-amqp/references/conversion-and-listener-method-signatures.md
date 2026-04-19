# Conversion and listener method signatures

Open this reference when the common-path JSON payload listener in [SKILL.md](../SKILL.md) is not enough and the blocker is payload conversion, method arguments, headers, validation, or custom listener signatures.

## Conversion blocker

**Problem:** the listener does not receive the expected payload type, or producers and consumers do not agree on message shape.

**Solution:** keep one converter strategy per contract and make the listener signature match that contract directly.

```java
@Bean
JacksonJsonMessageConverter jsonConverter() {
    return new JacksonJsonMessageConverter();
}
```

## Listener signature blocker

**Problem:** the listener needs payload plus headers, raw `Message` access, or validation metadata.

**Solution:** add only the arguments the handler actually needs.

```java
@RabbitListener(queues = "orders")
void handle(OrderCreated event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
    process(event, routingKey);
}
```

```java
@RabbitListener(queues = "orders")
void handle(Message message) {
    processRaw(message);
}
```

Use typed payload parameters for the ordinary path. Drop to raw `Message` only when headers, properties, or raw bytes are the real blocker.

## Validation blocker

**Problem:** messages deserialize but violate the business contract.

**Solution:** validate immediately after conversion and fail fast before side effects.

- Validate required fields before delegating to application services.
- Keep deserialization failure and business validation failure distinct in logs and retry handling.

## Decision points

| Situation | Choice |
| --- | --- |
| stable JSON contract | typed payload parameter |
| need routing key or delivery metadata | payload plus selected `@Header` arguments |
| need raw broker message properties | `Message` parameter |
| contract ambiguity between teams | freeze one converter strategy and document the payload shape |

## Pitfalls

- Do not mix multiple converter strategies in one module unless interoperability forces it.
- Do not expose raw broker details to business services when only the payload is needed.
- Do not hide validation failures inside generic listener exceptions.
