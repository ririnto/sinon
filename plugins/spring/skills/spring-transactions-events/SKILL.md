---
name: spring-transactions-events
description: >-
  Use this skill when the user asks to "use @Transactional correctly", "handle transactional events", "avoid dual writes", "design an outbox flow", or needs guidance on Spring transaction boundaries and post-commit side effects.
---

# Spring Transactions and Events

## Overview

Use this skill to design Spring transaction boundaries, rollback rules, transactional event publication, and outbox-style consistency around real service workflows. The common case is one service-layer write transaction, one deliberate post-commit side-effect strategy, and one explicit answer for what happens on checked exceptions, retries, and asynchronous delivery.

## Use This Skill When

- You are writing or reviewing `@Transactional` service methods.
- You need to decide between direct side effects, transactional events, and outbox patterns.
- You need propagation, rollback, or self-invocation guidance that matches Spring's proxy model.
- Do not use this skill when the question is only about one repository method or one Kafka listener annotation in isolation.

## Common-Case Workflow

1. Start from the write boundary and decide which service method owns the transaction.
2. Make rollback rules explicit, especially when checked exceptions or post-commit work are involved.
3. Keep direct database writes inside the transaction and move side effects to a deliberate post-commit path.
4. Use a transactional outbox when one business write and one asynchronous message must stay consistent.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one service-owned transaction boundary:

```java
@Service
class OrderService {

    private final OrderRepository orderRepository;

    OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    OrderEntity create(CreateOrderCommand command) {
        OrderEntity order = new OrderEntity(command.customerId(), OrderStatus.CREATED);
        return orderRepository.save(order);
    }
}
```

---

*Applies when:* one use case performs a normal write and no asynchronous side effect needs to happen inside the same method body.

## Ready-to-Adapt Templates

Checked-exception rollback rule:

```java
@Transactional(rollbackFor = IOException.class)
OrderEntity importOrder(InputStream payload) throws IOException {
    OrderEntity order = parser.parse(payload);
    return repository.save(order);
}
```

---

*Applies when:* the method can fail with checked exceptions and a partial commit would be incorrect.

Transactional event publication:

```java
@Service
class OrderService {

    private final ApplicationEventPublisher publisher;

    @Transactional
    OrderEntity create(CreateOrderCommand command) {
        OrderEntity order = repository.save(new OrderEntity(command.customerId(), OrderStatus.CREATED));
        publisher.publishEvent(new OrderCreatedEvent(order.getId()));
        return order;
    }
}

@Component
class OrderCreatedProjectionHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onOrderCreated(OrderCreatedEvent event) {
        // send email, enqueue async work, or call non-transactional collaborators
    }
}
```

---

*Applies when:* the side effect must not happen on rollback and does not need to be part of the main database transaction.

Transactional outbox write:

```java
@Transactional
OrderEntity create(CreateOrderCommand command) {
    OrderEntity order = repository.save(new OrderEntity(command.customerId(), OrderStatus.CREATED));
    outboxRepository.save(OutboxEvent.orderCreated(order.getId()));
    return order;
}
```

---

*Applies when:* the system must not lose the message when the database write succeeds but downstream delivery fails later.

## Validate the Result

Validate the common case with these checks:

- one service method clearly owns the write transaction
- checked-exception rollback behavior is explicit when it matters
- self-invocation is not silently bypassing `@Transactional`
- side effects happen either after commit or through a durable outbox path, not as an accidental dual write
- propagation and isolation choices are explained by the use case instead of copied by habit

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| propagation, rollback, self-invocation, or transaction callback semantics | `./references/transaction-boundaries.md` |
| durable DB + message consistency and replay-safe consumers | `./references/outbox-patterns.md` |
| event publication timing and post-commit side effects | `./references/event-publication.md` |

## Invariants

- MUST keep transaction ownership explicit at the service boundary.
- MUST NOT assume checked exceptions roll back automatically.
- MUST treat self-invocation as a proxy boundary hazard.
- SHOULD choose one post-commit strategy deliberately: callback, transactional event, or outbox.
- SHOULD use the outbox pattern when a database write and asynchronous message must stay consistent.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| annotating inner helper methods with `@Transactional` and calling them through `this` | proxy interception is bypassed, so the transaction semantics never activate | keep the transaction on the externally-invoked service method or use a separate bean/template |
| assuming all exceptions roll back | checked exceptions commit by default and can leave partial state behind | add `rollbackFor` or redesign the failure boundary |
| writing to the database and publishing to Kafka in the same method body with no outbox | one side can succeed while the other fails | co-write an outbox row and publish later |
| using `REQUIRES_NEW` without explaining why | nested transactional behavior becomes expensive and surprising | use it only when one independent commit really must survive outer rollback |

## Scope Boundaries

- Activate this skill for:
  - service-layer transaction boundaries
  - transactional events and post-commit side effects
  - outbox consistency patterns
- Do not use this skill as the primary source for:
  - plain repository method syntax
  - Kafka listener topology alone
  - generic distributed systems theory detached from Spring semantics
