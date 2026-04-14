---
title: Spring Data JPA Patterns Reference
description: >-
  Reference for Spring Data JPA entity mapping, projections, specifications, and fetch rules.
---

Use this reference when the repository boundary is clear and the remaining work is mapping, query shape, or transactional detail.

## Entity Mapping Rules

- map aggregates deliberately
- keep bidirectional relationships only when they truly help the model
- prefer explicit enum storage strategy

## Repository Method Rule

Start with derived query methods:

```java
Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable);
```

Move to explicit `@Query` only when the method name no longer explains the intent cleanly.

## Projection Recipe

Use projections when a read path needs only a subset of fields.

## Specification Recipe

Use specifications when query composition matters more than one fixed repository method.

```java
interface OrderRepository extends JpaRepository<OrderEntity, Long>, JpaSpecificationExecutor<OrderEntity> {
}
```

This is the point where the repository moves from simple derived methods toward composable query criteria.

## Transaction Boundary Rule

Put write consistency in service methods, not scattered controller calls.

Checked-exception rule:

- checked exceptions do not roll back by default
- add `rollbackFor = ...` when a checked failure must abort the write

```java
@Transactional(rollbackFor = IOException.class)
OrderEntity importOrder(InputStream payload) throws IOException {
    return repository.save(parser.parse(payload));
}
```

Self-invocation warning:

- `this.someTransactionalMethod()` bypasses Spring's proxy and does not activate a new transactional boundary
- keep the transaction on the externally-invoked service method or move the inner method to another bean

## Pagination and Fetch Rule

Use `Page` only when the caller truly needs total counts.

- use `Slice` when `hasNext` is enough and count-query cost is avoidable
- add explicit `countQuery` only when the generated count query is incorrect or too expensive

Use `@EntityGraph` or DTO projections when the real problem is fetch shape, not just method naming.

## Open Session in View Rule

Do not assume lazy loading during HTTP rendering is a safe default.

- prefer service-layer fetch design and explicit DTO mapping
- treat `spring.jpa.open-in-view=false` as the safer production posture when API boundaries should not depend on session leakage

## Fetch Strategy Check

Before changing fetch style, ask:

- is this read path actually hot
- is the problem a query count problem or a DTO shaping problem
- should the response use a DTO projection instead

## `@Modifying` Rule

When a repository method performs a bulk update or delete, keep the persistence-context side effects explicit.

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("update OrderEntity o set o.status = :status where o.id = :id")
int updateStatus(@Param("id") Long id, @Param("status") OrderStatus status);
```

## Common Mistakes

- exposing entities directly as public API payloads
- overusing custom queries when a derived query is clear enough
- letting transactional behavior remain implicit
- using `Page` when the caller only needs `hasNext`
- debugging N+1 issues only by flipping fetch type instead of reviewing projections, `@EntityGraph`, or DTO mapping
