# Spring Pulsar headers

Open this reference when accessing Pulsar message headers or configuring header mapping.

## Accessing headers in single-record listeners

Use `@Header` with Spring `PulsarHeaders` constants or the Pulsar `Consumer` object.

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse")
void handle(String data, @Header(PulsarHeaders.MESSAGE_ID) MessageId messageId,
        @Header(PulsarHeaders.TOPIC_NAME) String topic) {
    log.info("Received {} from topic {} with id {}", data, topic, messageId);
}
```

## Accessing headers in batch listeners

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse", batch = true)
void handle(List<String> data, @Header(PulsarHeaders.MESSAGE_ID) List<MessageId> messageIds) {
    for (int i = 0; i < data.size(); i++) {
        log.info("Record {} has id {}", data.get(i), messageIds.get(i));
    }
}
```

## Message header mapping

`PulsarHeaderMapper` maps between Pulsar user properties and Spring `MessageHeaders`.
Two implementations are available:

- `JsonPulsarHeaderMapper` -- serializes and deserializes complex header values as JSON.
- `ToStringPulsarHeaderMapper` -- converts header values to and from strings using `toString`.

```java
@Bean
JsonPulsarHeaderMapper pulsarHeaderMapper() {
    return JsonPulsarHeaderMapper.builder()
        .trustedPackages("com.example.events", "com.example.dto")
        .build();
}
```

## Decision points

- Use `@Header` for simple header access in listeners.
- Use `JsonPulsarHeaderMapper` when complex objects must travel as headers between producers and consumers.
- Set trusted packages on JSON header mappers to control which types can be deserialized.
- Keep header mapping simple.
  - Avoid sending large payloads as headers.
