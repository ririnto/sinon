---
name: spring-data-jpa
description: >-
  This skill should be used when the user asks to "model a Spring Data JPA entity", "write a Spring Data JPA repository", "use query methods", "review JPA pagination or transactions", or needs guidance on Spring Data JPA patterns.
---

# Spring Data JPA

## Overview

Use this skill to design Spring Data JPA entities, repositories, transactions, and query shapes around aggregate boundaries and practical persistence needs. The common case is one aggregate-focused entity, one repository with a clear derived query, and one explicit service-side transaction boundary. Focus on repository contract and persistence semantics before optimizing query details.

## Use This Skill When

- You are modeling JPA entities or repositories.
- You need pagination, derived query methods, or transaction-aware writes.
- You need a default Spring Data JPA shape you can paste into a codebase.
- Do not use this skill when the main persistence model is reactive, Redis-style key/value, or simpler aggregate persistence without JPA semantics.

## Common-Case Workflow

1. Identify the aggregate and repository boundary.
2. Model entities for persistence without leaking every internal detail into public API contracts.
3. Start with derived query methods and add explicit queries only when the contract really needs them.
4. Keep write consistency explicit with service-side transaction boundaries.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one aggregate entity and one derived query:

```java
@Entity
class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Enumerated(EnumType.STRING)
    OrderStatus status;
}

interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable);
}
```

---

*Applies when:* you need the default JPA repository path before adding projections or custom queries.

## Ready-to-Adapt Templates

Aggregate entity:

```java
@Entity
class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Enumerated(EnumType.STRING)
    OrderStatus status;
}
```

---

*Applies when:* the domain object needs JPA persistence semantics.

Derived query repository:

```java
interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable);
}
```

---

*Applies when:* the query intent is simple enough that the method name still reads clearly.

Transactional write service:

```java
@Service
class OrderService {
    private final OrderRepository orderRepository;

    OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    OrderEntity create(OrderEntity order) {
        return orderRepository.save(order);
    }
}
```

---

*Applies when:* a write path needs clear transactional semantics.

Projection read path:

```java
interface OrderSummary {
    Long getId();
    OrderStatus getStatus();
}

interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Page<OrderSummary> findByStatus(OrderStatus status, Pageable pageable);
}
```

---

*Applies when:* the read path only needs a subset of fields.

## Validate the Result

Validate the common case with these checks:

- repository boundaries align with aggregates rather than raw tables
- simple derived queries are used before custom `@Query` methods
- write consistency is explicit in service-layer transactions
- persistence entities are not exposed directly as external transport contracts unless that is deliberate and safe

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| projections, specifications, fetch strategy, or custom query escalation | `./references/jpa-patterns.md` |

## Invariants

- MUST define repository boundaries around aggregates, not tables alone.
- SHOULD prefer simple query methods before custom queries.
- MUST keep transaction semantics explicit for writes.
- SHOULD avoid leaking persistence entities directly into public transport models.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| exposing JPA entities directly from external APIs | persistence concerns leak into public contracts | use DTOs or projections for transport shapes |
| adding custom queries before simpler repository methods are exhausted | repository intent becomes harder to scan | start with a derived query and escalate only if needed |
| hiding write consistency behind implicit transaction behavior | failure handling becomes ambiguous | place write transactions explicitly in service methods |

## Scope Boundaries

- Activate this skill for:
  - Spring Data JPA entity and repository work
  - transactional write boundaries
  - query methods and pagination
- Do not use this skill as the primary source for:
  - simpler aggregate persistence without JPA semantics
  - reactive relational persistence
  - Redis key-value modeling
