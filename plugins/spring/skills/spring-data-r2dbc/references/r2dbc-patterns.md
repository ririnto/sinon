---
title: Spring Data R2DBC Patterns Reference
description: >-
  Reference for Spring Data R2DBC repository shape, reactivity checks, and usage patterns.
---

Use this reference when reactive persistence is already justified and the remaining work is shaping repository and flow boundaries correctly.

## End-to-End Reactivity Check

Use R2DBC when:

- the surrounding app is reactive
- the persistence path must stay non-blocking
- the team is prepared to keep blocking APIs out of the flow

## Transaction Boundary Note

Keep transaction design explicit and aligned with reactive composition instead of assuming imperative transaction habits will transfer unchanged.

Use `TransactionalOperator` when the reactive chain itself should own the transactional boundary.

```java
Mono<OrderEntity> create(OrderEntity order) {
    return transactionalOperator.transactional(repository.save(order));
}
```

Important note:

- cancellation and timeout behavior can still interrupt a reactive write flow, so do not hide transactional assumptions behind broad operator chains

## Common Mistakes

- mixing blocking repositories into reactive services
- forcing reactive persistence into an otherwise blocking application without a clear benefit
- assuming imperative transaction habits transfer unchanged to Reactor chains
