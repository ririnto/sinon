# Spring Kafka share consumers (KIP-932)

Open this reference when building share consumer listeners that need manual acknowledgment, error recovery, concurrency configuration, or lifecycle event integration.

## Share consumer boundary

Share consumers (Kafka Queues) use the `ShareConsumer` client API and allow multiple consumers in the same share group to cooperatively consume from the same partitions.
The Kafka broker distributes records at the record level, so each partition can be consumed by multiple consumers simultaneously.
This differs from consumer groups where each partition is exclusively assigned to one consumer.

Share consumers do not support batch processing, topic patterns, explicit partition assignment, or the `CommonErrorHandler` interface.

## Manual acknowledgment shape

```java
@Bean
ShareKafkaListenerContainerFactory<String, String> manualShareKafkaListenerContainerFactory(ShareConsumerFactory<String, String> shareConsumerFactory) {
    ShareKafkaListenerContainerFactory<String, String> factory = new ShareKafkaListenerContainerFactory<>(shareConsumerFactory);
    factory.getContainerProperties().setShareAckMode(ContainerProperties.ShareAckMode.MANUAL);
    return factory;
}

@KafkaListener(topics = "order-queue", containerFactory = "manualShareKafkaListenerContainerFactory")
void processOrder(ConsumerRecord<String, String> record, ShareAcknowledgment acknowledgment) {
    try {
        processOrderLogic(record.value());
        acknowledgment.acknowledge();
    } catch (TransientException e) {
        acknowledgment.release();
    } catch (Exception e) {
        acknowledgment.reject();
    }
}
```

## Acknowledgment modes

| Mode | Who acknowledges | On success | On listener error |
| --- | --- | --- | --- |
| `EXPLICIT` (default) | Container | Container sends ACCEPT | Recoverer decides (REJECT by default) |
| `MANUAL` | Listener code | Listener calls `acknowledge()` | Listener calls `release()` or `reject()` |
| `IMPLICIT` | Kafka broker | Broker auto-ACCEPTs | Broker auto-ACCEPTs (no recovery) |

Use `MANUAL` mode when business logic determines the acknowledgment outcome record by record.
Use `IMPLICIT` mode only when per-record delivery guarantees are not required.

In `MANUAL` mode, subsequent polls are blocked until all records from the previous poll are acknowledged.
Call `renew()` to extend the acquisition lock when processing exceeds the broker's lock duration (`group.share.record.lock.duration.ms`, default 30 seconds).

## Sync vs async commits

Set `syncShareCommits` to `false` on `ContainerProperties` to use `commitAsync()` instead of the default `commitSync()` when slightly relaxed ack-durability is acceptable in exchange for higher throughput.

## Error handling

Share consumers do not use `CommonErrorHandler`.
Error recovery uses the `ShareConsumerRecordRecoverer` interface instead.

Poll-level: `RecordDeserializationException` and `CorruptRecordException` from `poll()` are caught so the consumer thread continues.

Listener-level: A `ShareConsumerRecordRecoverer` decides ACCEPT, RELEASE, or REJECT when the listener throws.
The default is `ShareConsumerRecordRecoverer.REJECTING`.
Set a custom recoverer on the factory or container.

```java
factory.setShareConsumerRecordRecoverer((record, ex) -> {
    if (ex instanceof TransientException) {
        return AcknowledgeType.RELEASE;
    }
    return AcknowledgeType.REJECT;
});
```

The broker limits redelivery through `group.share.delivery.count.limit` (default 5).
After the limit, records are archived and not redelivered regardless of recoverer behavior.

## Concurrency

Share containers support concurrent processing by creating multiple `ShareConsumer` threads within a single container.
Unlike consumer groups, concurrency in share groups is additive across application instances.

```java
container.setConcurrency(5);
```

Override per-listener:

```java
@KafkaListener(topics = "high-throughput-topic", containerFactory = "shareKafkaListenerContainerFactory", concurrency = "10")
void handle(ConsumerRecord<String, String> record) {
    process(record.value());
}
```

## Verification rule

Verify one share consumer test proves record distribution across consumer threads, correct acknowledgment behavior for ACCEPT, RELEASE, and REJECT, and that the delivery count limit prevents poison message loops.

## Decision points

| Situation | Use |
| --- | --- |
| Cooperative consumption from the same partitions | share consumer |
| Partition-exclusive assignment | regular consumer group |
| Record-level acknowledgment control | `ShareAckMode.MANUAL` |
| Container-managed acknowledgment with recoverer | `ShareAckMode.EXPLICIT` (default) |
| No delivery guarantees needed | `ShareAckMode.IMPLICIT` |
| Higher throughput, relaxed ack durability | `syncShareCommits = false` |
