# Spring Kafka listener replay

Open this reference when the consumer must seek or replay records deliberately.

## Seek-aware listener shape

Use seeking only when replay behavior is a deliberate operational or recovery tool.

```java
@Component
class ReplayListener implements ConsumerSeekAware {
    @KafkaListener(topics = "accounts", groupId = "projection-rebuild")
    void handle(AccountEvent event) {
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        assignments.keySet().forEach(partition -> callback.seekToBeginning(partition.topic(), partition.partition()));
    }
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Operational replay or recovery | manual seek |
| Ordinary consumer does not need replay control | stay on the common path |
