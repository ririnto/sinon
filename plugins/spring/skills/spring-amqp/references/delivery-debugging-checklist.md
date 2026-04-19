# Delivery debugging

Open this reference when the ordinary producer and consumer path in [SKILL.md](../SKILL.md) is not enough and the blocker is delivery diagnosis.

## Debugging blocker

**Problem:** message delivery or listener behavior is wrong and logs do not explain whether the problem is topology, conversion, or broker availability.

**Solution:** log delivery identifiers and topology decisions at the messaging seam without leaking sensitive payload data.

```java
log.atInfo().log(() -> "queue=orders routingKey=orders.created messageId=" + message.getMessageProperties().getMessageId());
```

## Decision points

| Symptom | First check |
| --- | --- |
| message not delivered | queue, exchange, binding, and routing-key contract |
| payload conversion fails | converter configuration and listener signature |
| unexpected redelivery | retry and recoverer policy plus listener idempotency |

## Pitfalls

- Do not debug delivery issues without logging queue, exchange, and routing-key identifiers.
- Do not collapse topology, conversion, and broker-connectivity failures into one generic error log.
