# Spring Kafka tombstones

Open this reference when the topic uses compacted-record delete semantics.

## Tombstone handling

Treat `null` values as explicit delete semantics only for topics that actually use compacted-topic patterns.

## Producer shape

```java
void deleteAccount(String accountId) {
    kafkaTemplate.send("accounts", accountId, null);
}
```

```java
@KafkaListener(topics = "accounts")
void handle(@Payload(required = false) AccountEvent event, @Header(KafkaHeaders.RECEIVED_KEY) String key) {
    if (event == null) {
        accountProjection.delete(key);
    }
}
```

## Verification rule

Verify one integration test publishes a `null` value and proves the listener treats it as a delete marker only for the compacted topic that is meant to use tombstones.

## Decision points

| Situation | Use |
| --- | --- |
| Compacted topic uses delete semantics | tombstone-aware listener |
| Ordinary topic has no delete marker semantics | stay on the common path |

## Gotchas

- Do not treat `null` values as ordinary deserialization failures when the topic is compacted and delete semantics are intentional.
- Do not reuse a tombstone-aware listener shape on topics that never model deletes through compaction.
