# Spring Pulsar schema mapping and compatibility

Open this reference when schema type, schema evolution, or message conversion compatibility is unclear.

## First safe schema rule

Choose one schema strategy per topic before multiple producers or consumers depend on it.

## Listener declaration shape

```java
@PulsarListener(
    topics = "shipments",
    subscriptionName = "warehouse",
    schemaType = SchemaType.JSON
)
void handle(ShipmentEvent event) {
    service.handle(event);
}
```

## Producer shape

```java
Schema<ShipmentEvent> schema = Schema.JSON(ShipmentEvent.class);

pulsarTemplate.newMessage(schema)
    .withTopic("shipments")
    .withValue(event)
    .send();
```

## Compatibility rules

- Use JSON for the ordinary Spring-to-Spring path unless cross-language contracts require Avro, Protobuf, or native bytes.
- Keep schema changes backward compatible while mixed producer and consumer versions coexist.
- Use one object mapper policy per topic. Avoid silently mixing custom serializers.

## When to customize

Open a custom schema resolver path only when topic-specific schema rules cannot be expressed with `schemaType`, `Schema.JSON(...)`, or the default Boot configuration.
