---
title: Spring Transactional Event Publication Reference
description: >-
  Reference for Spring application events, transactional event phases, and post-commit side effects.
---

Use this reference when the write transaction should stay small but some side effect must happen only after commit or rollback is known.

## Transactional Event Phase Comparison

Choose the phase deliberately:

| Phase | When the listener runs | Typical use |
| --- | --- | --- |
| `BEFORE_COMMIT` | just before the transaction commits | last-moment validation or mutation |
| `AFTER_COMMIT` | after the transaction commits | side effects that must never happen on rollback |
| `AFTER_ROLLBACK` | after the transaction rolls back | cleanup or rollback-specific follow-up |
| `AFTER_COMPLETION` | after commit or rollback | same callback must observe either outcome |

Important notes:

- `BEFORE_COMMIT` listeners that perform remote I/O can hold the transaction open longer than expected
- `AFTER_COMMIT` is the safest choice for side effects that should never be replayed on rollback
- `AFTER_COMPLETION` is useful for releasing resources that must run regardless of outcome

## Fallback Execution

By default, transactional event listeners do not run when no transaction is active.

Use `fallbackExecution = true` only when the same listener still makes sense outside a transaction:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
void onOrderCreated(OrderCreatedEvent event) {
    // runs even if called outside a transaction context
}
```

## Post-Commit Database Write Rule

Do not assume that a database write inside an `AFTER_COMMIT` listener belongs to the original transaction.

- if the follow-up write must be durable on its own, give it its own transaction boundary
- if the side effect is just messaging, logging, or notification, keep it non-transactional

## Operational Mistakes

- using plain `@EventListener` when the side effect must wait until commit
- putting heavy remote I/O in `BEFORE_COMMIT`
- assuming `fallbackExecution = true` is harmless for all listeners
- mixing business events and technical retry events in the same listener path
