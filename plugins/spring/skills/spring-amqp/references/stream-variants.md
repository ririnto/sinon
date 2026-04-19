# Stream variants

Open this reference when the ordinary queue-based RabbitMQ path in [SKILL.md](../SKILL.md) is not enough and the blocker is RabbitMQ stream semantics.

## Stream plugin blocker

**Problem:** queue semantics are not enough because throughput, replay, or stream-specific delivery behavior is required.

**Solution:** use the RabbitMQ stream plugin explicitly and keep stream semantics separate from ordinary queue semantics.

```java
RabbitStreamTemplate streamTemplate = new RabbitStreamTemplate(environment, "orders-stream");
```

```java
StreamListenerContainer container = new StreamListenerContainer(environment);
container.setupMessageListener("orders-stream", message -> {
});
```

Do not mix queue and stream semantics casually in one module.

## Pitfalls

- Do not use stream APIs as a drop-in replacement for ordinary queue consumers.
- Do not mix stream and queue operational assumptions in one listener design.
