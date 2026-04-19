# Polling receive

Open this reference when the ordinary event-driven listener path in [SKILL.md](../SKILL.md) is not enough and the blocker is pull-style receive.

## Polling consumer blocker

**Problem:** the caller needs pull-style receive behavior instead of container-managed asynchronous consumption.

**Solution:** use `RabbitTemplate` receive operations only when the consumer really is synchronous or scheduled.

```java
Message message = rabbitTemplate.receive("orders", 5000);
```

## Decision points

| Situation | First choice |
| --- | --- |
| the flow is scheduled or command-driven | polling receive |

## Pitfalls

- Do not use polling receive as a substitute for a normal event-driven consumer.
- Keep timeout expectations explicit at the call site.
