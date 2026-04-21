# Spring Kafka listener replay

Open this reference when the consumer must seek or replay records deliberately.

## Seek-aware listener shape

Use seeking only when replay behavior is a deliberate operational or recovery tool.

```java
@Component
class ReplayListener implements ConsumerSeekAware {
    @KafkaListener(id = "projection-rebuild", topics = "accounts", groupId = "projection-rebuild")
    void handle(AccountEvent event) {
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        assignments.keySet().forEach(partition -> callback.seekToBeginning(partition.topic(), partition.partition()));
    }
}
```

## Replay trigger shape

```java
@Autowired
KafkaListenerEndpointRegistry registry;

void restartReplayListener() {
    MessageListenerContainer container = registry.getListenerContainer("projection-rebuild");
    container.stop();
    container.start();
}
```

Use an explicit listener `id` whenever operations or tests need to address the listener through `KafkaListenerEndpointRegistry`.

## Decision points

| Situation | Use |
| --- | --- |
| Operational replay or recovery | manual seek |
| Ordinary consumer does not need replay control | stay on the common path |

## Verification rule

Verify one replay path proves the consumer starts at the intended offset boundary and does not silently reuse the previous committed position.
