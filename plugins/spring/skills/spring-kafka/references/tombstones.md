# Spring Kafka tombstones

Open this reference when the topic uses compacted-record delete semantics.

## Tombstone handling

Treat `null` values as explicit delete semantics only for topics that actually use compacted-topic patterns.

```java
@KafkaListener(topics = "accounts")
void handle(@Payload(required = false) AccountEvent event, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
    if (event == null) {
        accountProjection.delete(key);
    }
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Compacted topic uses delete semantics | tombstone-aware listener |
| Ordinary topic has no delete marker semantics | stay on the common path |
