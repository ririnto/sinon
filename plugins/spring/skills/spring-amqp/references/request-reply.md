# Request-reply

Open this reference when the ordinary event-style path in [SKILL.md](../SKILL.md) is not enough and the blocker is synchronous broker-mediated reply semantics.

## Request-reply blocker

**Problem:** the caller truly needs a synchronous reply from a RabbitMQ-mediated interaction.

**Solution:** use request-reply explicitly and set a reply timeout.

```java
Object reply = rabbitTemplate.convertSendAndReceive("rpc.exchange", "rpc.key", request);
```

```java
rabbitTemplate.setReplyTimeout(5000);
```

Prefer event-style messaging unless synchronous reply is part of the integration contract.

## Pitfalls

- Do not introduce request-reply when asynchronous messaging is sufficient.
- Do not leave the reply timeout implicit.
- Do not use request-reply to hide a missing service API boundary.
