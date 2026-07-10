# Spring Pulsar schema mapping and compatibility

Open this reference when schema type, schema evolution, or message conversion compatibility is unclear.

## First safe schema rule

Choose one schema strategy per topic before multiple producers or consumers depend on it.

## Listener declaration shape

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse", schemaType = SchemaType.JSON)
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

## Custom schema mapping

Three mechanisms are available for configuring default schema information per message type.
Use the one that fits your configuration model.

### Configuration properties

```yaml
spring:
  pulsar:
    defaults:
      type-mappings:
        - message-type: com.example.ShipmentEvent
          schema-info:
            schema-type: JSON
        - message-type: com.example.AuditRecord
          schema-info:
            schema-type: AVRO
```

The `message-type` is the fully-qualified class name.
When configured, producers and consumers consult this mapping before falling back to the default schema inference.

### Schema resolver customizer

```java
@Bean
SchemaResolverCustomizer<DefaultSchemaResolver> schemaResolverCustomizer() {
    return schemaResolver -> {
        schemaResolver.addCustomSchemaMapping(ShipmentEvent.class, Schema.JSON(ShipmentEvent.class));
        schemaResolver.addCustomSchemaMapping(AuditRecord.class, Schema.AVRO(AuditRecord.class));
    };
}
```

Use the customizer when properties are not enough, such as when schema objects require custom configuration beyond the type name.

### Type mapping annotation

```java
@PulsarMessage(schemaType = SchemaType.JSON)
record ShipmentEvent(String shipmentId, String status) {
}
```

The `@PulsarMessage` annotation on the message class eliminates the need to specify `schemaType` on every `@PulsarListener` or template send operation for that type.

## AUTO_CONSUME and AUTO_PRODUCE_BYTES

When the schema type is not known in advance, use `AUTO_CONSUME` on consumers and `AUTO_PRODUCE_BYTES` on producers.

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse", schemaType = SchemaType.AUTO_CONSUME)
void handle(GenericRecord record) {
    log.info("Received: {}", record);
}
```

```java
void sendRaw(PulsarTemplate<byte[]> template, byte[] payload) {
    template.send("shipments", payload, Schema.AUTO_PRODUCE_BYTES());
}
```

## Compatibility rules

- Use JSON for the ordinary Spring-to-Spring path unless cross-language contracts require Avro, Protobuf, or native bytes.
- Keep schema changes backward compatible while mixed producer and consumer versions coexist.
- Use one object mapper policy per topic.
  - Avoid silently mixing custom serializers.

## When to customize

Open a custom schema resolver path only when topic-specific schema rules cannot be expressed with `schemaType`, `Schema.JSON(...)`, type mappings, or the default Boot configuration.
