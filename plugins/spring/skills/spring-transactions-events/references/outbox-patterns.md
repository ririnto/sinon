---
title: Spring Transactional Outbox Patterns
description: >-
  Reference for transactional outbox design, polling relays, and idempotent consumer rules in Spring applications.
---

Use this reference when one database write and one asynchronous message must stay consistent even when delivery fails later.

## Polling Relay Details

Publish from a separate worker using a scheduled task:

```java
@Scheduled(fixedDelay = 5_000)
void publishOutbox() {
    List<OutboxEvent> batch = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
    for (OutboxEvent event : batch) {
        kafkaTemplate.send("orders.created", event.aggregateId, event);
        event.published = true;
        outboxRepository.save(event);
    }
}
```

Refine further with:

- explicit locking such as `FOR UPDATE SKIP LOCKED` to prevent multiple instances from picking up the same events
- batch sizing and backoff to tune throughput and reduce broker pressure
- retry and poison-event handling to move permanently failed events to a dead-letter table

## Locking and Backoff Notes

When multiple application instances share the same outbox table:

- use `FOR UPDATE SKIP LOCKED` in the polling query so only one instance picks up each batch
- implement idempotent publish logic in the relay to tolerate redelivery
- consider exponential backoff on publish failures rather than immediate retry

## Idempotent Consumer Rule

Outbox delivery is normally at-least-once, so consumers must tolerate duplicates:

```java
@KafkaListener(topics = "orders.created", groupId = "billing")
void onOrderCreated(OrderCreatedEvent event) {
    if (processedMessageRepository.existsById(event.id())) {
        return;
    }
    billingService.handle(event);
    processedMessageRepository.save(new ProcessedMessage(event.id()));
}
```

Key points:

- use the event ID (not the payload) as the idempotency key
- record processed messages after successful handling, not before
- keep the idempotency check and the business handling in the same transactional boundary to avoid duplicates on crash

## Operational Mistakes

- marking the outbox row published before broker delivery is confirmed
- skipping consumer idempotency because duplicates seem unlikely
- treating the outbox as a generic audit table instead of one delivery queue with clear cleanup rules
- publishing from the request thread and calling it an outbox design

## See Also

- Kafka delivery semantics, listener topology, and failure-policy heuristics should be documented alongside the active messaging design instead of by linking to another skill.
