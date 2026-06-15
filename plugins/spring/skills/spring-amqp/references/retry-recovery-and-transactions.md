# Retry, recovery, and transactions

Open this reference when the baseline retry and dead-letter path in [`SKILL.md`](../SKILL.md) is not enough and the blocker is recoverer choice, transactional semantics, or deeper failure handling.

## Exhausted-message outcome

Problem: the listener fails repeatedly, and the application needs a deliberate exhausted-message outcome.

Solution: choose the exhausted-message destination first, then pair it with one retry strategy.

- Use `RejectAndDontRequeueRecoverer` when the broker DLX or DLQ path already owns exhausted-message handling.
- Use `RepublishMessageRecoverer` when the application must republish failed messages to an explicit error exchange.

```java
RetryOperationsInterceptor interceptor = RetryInterceptorBuilder.stateless()
    .maxAttempts(3)
    .backOffOptions(1000, 2.0, 5000)
    .recoverer(new RejectAndDontRequeueRecoverer())
    .build();
```

Use stateless retry for idempotent handlers.
Use transactional retry only when message handling truly needs transactional semantics.

When exhausted messages must be inspected or replayed later, republish them explicitly instead of silently dropping them.

```java
MessageRecoverer recoverer = new RepublishMessageRecoverer(rabbitTemplate, "orders.error.exchange", "orders.failed");
```

Use `RejectAndDontRequeueRecoverer` only when the broker DLX path already owns dead-letter handling.

## Transactional listener path

Problem: the listener must coordinate broker acknowledgment with local transactional work.

Solution: enable transacted channels only on the paths that truly need them.

```java
factory.setChannelTransacted(true);
```

Transactional containers add cost and operational complexity.
Do not enable them by default.

## Failure classification

Distinguish between:

- conversion or validation failures,
- transient downstream failures,
- permanent business failures.

Those categories often need different retry and dead-letter outcomes.

## Decision points

| Situation | First choice |
| --- | --- |
| broker DLX already owns exhausted messages | `RejectAndDontRequeueRecoverer` plus one explicit retry policy |
| application must republish failed messages | `RepublishMessageRecoverer` plus one explicit retry policy |
| broker acknowledgment must align with local transaction work | transactional listener path only on that consumer |
| failures differ by permanence or retryability | classify failures before hardening retry behavior |

## Pitfalls

- Do not combine multiple retry strategies on one listener path without a clear reason.
- Do not use transactions to paper over non-idempotent business logic.
- Do not treat recoverer choice as an afterthought.
  - It defines the exhausted-message contract.
- When deploying against RabbitMQ 4.3+, delivery-count semantics changed: `x-delivery-count` is now managed by the broker and is not incremented by the consumer on reject/nack.
  - Retry and dead-letter policies that relied on the previous consumer-managed delivery-count behavior must be reviewed and adjusted.

## Fatal exception handling (4.1)

Problem: fatal exceptions such as `MessageConversionException` or `MethodArgumentNotValidException` cause message rejection and requeue loops.

Solution: configure `ConditionalRejectingErrorHandler` with `stopListenerOnFatal(true)` to stop the container on fatal errors and requeue the message for other consumers.
See [`container-variants-and-concurrency.md`](container-variants-and-concurrency.md) for the container-level configuration.

The default `ConditionalRejectingErrorHandler` rejects messages with fatal exceptions without requeuing.
With `stopListenerOnFatal(true)`, the container stops entirely, preventing the requeue loop while making the failure visible to operators.
