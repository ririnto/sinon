# Publisher confirms, returns, and send reliability

Open this reference when the ordinary `RabbitTemplate` publish path in [SKILL.md](../SKILL.md) is not enough and the blocker is broker acknowledgment, unroutable-message handling, or send-side delivery certainty.

## Publisher confirm blocker

**Problem:** the producer must know whether the broker accepted the publish request.

**Solution:** enable publisher confirms and treat the confirm outcome as a producer-side contract.

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
```

Use confirms when the application must distinguish 'publish attempted' from 'broker accepted'.

## Publisher return blocker

**Problem:** the message reaches the exchange publish path but cannot be routed to any queue.

**Solution:** enable returns and mandatory publishing so unroutable messages are surfaced explicitly.

```yaml
spring:
  rabbitmq:
    publisher-returns: true
    template:
      mandatory: true
```

## Send-side failure blocker

**Problem:** producer failures are being handled like consumer retry or DLQ failures, which hides the actual failure point.

**Solution:** keep send-side publish reliability separate from consumer-side retry and dead-letter behavior.

- Producer confirms and returns answer 'was the message accepted and routed?'
- Consumer retry and DLQ answer 'what happened after a consumer received the message?'

## Decision points

| Situation | First choice |
| --- | --- |
| Need broker acceptance visibility | publisher confirms |
| Need explicit unroutable-message handling | publisher returns + mandatory publishing |
| Need both routing and broker acceptance guarantees | confirms plus returns |
| Need only fire-and-forget event publishing | stay on the ordinary path |

## Pitfalls

- Do not treat confirms and returns as consumer retry features; they solve a different failure stage.
- Do not enable send-side reliability features without deciding how the producer records or reacts to failures.
- Do not assume exchange publish success means the message was actually routed to a queue.
