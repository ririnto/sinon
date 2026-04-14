---
title: Spring Transaction Boundaries Reference
description: >-
  Reference for Spring transaction propagation, rollback behavior, self-invocation, and transaction callbacks.
---

Use this reference when the main question is where the transaction should start, what rolls back, and what really happens at nested boundaries.

## Isolation and Existing Transactions

Local isolation, timeout, and read-only hints do not magically override an already-running outer transaction.

- if the method joins an existing transaction, the outer characteristics usually win
- do not assume `@Transactional(isolation = ...)` changes behavior when no new transaction actually starts

## UnexpectedRollback Rule

One inner logical scope can mark the overall transaction rollback-only.

That means:

- outer code may appear to succeed
- Spring can still throw `UnexpectedRollbackException` when commit is attempted

Use this as a signal that one inner unit decided the whole transaction must fail.

## Transaction Callback Rule

Use transaction callbacks only when the timing relative to commit or rollback really matters.

```java
@Transactional
void createOrder(OrderEntity order) {
    repository.save(order);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
            notifier.notifyOrderCreated(order.getId());
        }
    });
}
```

Important notes:

- `afterCommit` runs after the main transaction commits
- `afterCompletion` runs after commit or rollback
- if callback code needs its own database write, give that write its own transaction instead of assuming it can extend the old one safely

## Propagation Comparison

| Propagation | When a new transaction starts | Effect on outer transaction |
| --- | --- | --- |
| `REQUIRED` (default) | joins if one exists | outer rolls back if inner rolls back |
| `REQUIRES_NEW` | always starts a new one | fully independent; outer waits for inner to complete |
| `NESTED` | uses savepoint if supported | outer rolls back to savepoint if inner rolls back; requires savepoint-capable manager |

Key nuance:

- `REQUIRES_NEW` allocates an independent transaction and can increase connection pressure under load
- `NESTED` relies on the transaction manager supporting savepoints, which JPA/JDBC typically do but not all managers guarantee
