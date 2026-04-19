# Async return listeners

Open this reference when the ordinary listener path in [SKILL.md](../SKILL.md) is not enough and the blocker is asynchronous listener return handling.

## Async return blocker

**Problem:** the listener must return asynchronously without blocking the consumer thread for the full downstream operation.

**Solution:** use an async return type deliberately and verify acknowledgment timing against the container policy.

```java
@RabbitListener(queues = "orders")
CompletableFuture<Void> handleAsync(OrderCreated event) {
    return CompletableFuture.runAsync(() -> process(event));
}
```

## Decision points

| Situation | First choice |
| --- | --- |
| downstream work must complete asynchronously | async return type with explicit ack review |

## Pitfalls

- Do not assume async returns preserve the same acknowledgment semantics as synchronous listeners.
- Keep downstream completion and failure paths observable.
