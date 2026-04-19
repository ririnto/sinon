# Batch listeners

Open this reference when the ordinary one-message-per-listener path in [SKILL.md](../SKILL.md) is not enough and the blocker is whole-batch consumption.

## Batch listener blocker

**Problem:** one-by-one listener handling is too expensive and the workload should be processed in batches.

**Solution:** make batching explicit in the listener contract and verify ordering plus partial-failure behavior.

```java
@RabbitListener(queues = "orders.batch")
void handle(List<OrderCreated> events) {
    events.forEach(this::process);
}
```

## Decision points

| Situation | First choice |
| --- | --- |
| throughput gain depends on whole-batch processing | batch listener |

## Pitfalls

- Do not introduce batching without a clear partial-failure policy.
- Do not mix batch and single-message assumptions in one handler contract.
