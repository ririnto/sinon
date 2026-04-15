---
name: spring-data-jdbc
description: >-
  Use this skill when the user asks to "use Spring Data JDBC", "model a Spring Data JDBC aggregate", "choose JDBC over JPA", or needs guidance on aggregate-oriented relational persistence with Spring Data JDBC.
---

# Spring Data JDBC

## Overview

Use this skill to design Spring Data JDBC aggregates, repositories, and SQL-backed persistence with explicit aggregate boundaries and simpler relational behavior than JPA. The common case is one aggregate root, one straightforward repository, and one persistence model that does not assume lazy loading or ORM-style change tracking. Focus on direct aggregate persistence semantics rather than ORM abstractions.

## Use This Skill When

- You are modeling aggregate-oriented relational persistence without JPA complexity.
- You need to decide whether Spring Data JDBC fits better than JPA.
- You need a default Spring Data JDBC aggregate and repository shape.
- Do not use this skill when the problem requires full JPA behavior or reactive relational access.

## Common-Case Workflow

1. Start from the aggregate root.
2. Choose Spring Data JDBC when simpler aggregate persistence is a better fit than full JPA behavior.
3. Keep repository contracts centered on aggregate operations.
4. Prefer straightforward persistence rules over ORM-style abstraction expectations.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-jdbc</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one aggregate and one repository:

```java
@Table("orders")
public class OrderAggregate {
    @Id
    Long id;
    String customerId;
    @MappedCollection(idColumn = "order_id")
    Set<OrderLine> lines;
}

public record OrderLine(String sku, int quantity) {
}

interface OrderRepository extends CrudRepository<OrderAggregate, Long> {
}
```

---

*Applies when:* you need the default Spring Data JDBC path before adding custom queries or lower-level JDBC work.

## Ready-to-Adapt Templates

Aggregate root:

```java
@Table("orders")
public class OrderAggregate {
    @Id
    Long id;
    String customerId;
}
```

---

*Applies when:* the domain naturally centers on one aggregate root and simpler persistence semantics.

Repository:

```java
interface OrderRepository extends CrudRepository<OrderAggregate, Long> {
}
```

---

*Applies when:* CRUD-style aggregate operations are the main requirement.

Application service:

```java
@Service
class OrderApplicationService {
    private final OrderRepository orderRepository;

    OrderApplicationService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
}
```

---

*Applies when:* the aggregate needs application-level orchestration around repository operations.

## Validate the Result

Validate the common case with these checks:

- the model is organized around aggregate boundaries rather than raw tables alone
- repository methods stay aggregate-oriented
- no JPA-style lazy-loading or change-tracking assumptions leak into the design
- lower-level JDBC work is introduced only when repository semantics are not enough

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| aggregate modeling and repository guidance details | `./references/jdbc-patterns.md` |

## Invariants

- MUST model aggregate roots explicitly.
- SHOULD choose JDBC when simpler persistence semantics are a better fit than JPA.
- MUST keep repository methods aggregate-oriented.
- SHOULD not assume JPA-style behavior where Spring Data JDBC is intentionally simpler.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| expecting lazy-loading or rich ORM behavior from JDBC repositories | Spring Data JDBC intentionally does less than JPA | design around explicit aggregate persistence instead |
| mapping around tables instead of aggregate boundaries | repository intent becomes weaker and domain shape gets blurred | start from the aggregate root |
| choosing JDBC while still designing with JPA assumptions | the model and persistence behavior diverge | simplify the persistence model to match JDBC semantics |

## Scope Boundaries

- Activate this skill for:
  - aggregate-oriented JDBC persistence
  - Spring Data JDBC repositories
  - simpler relational mapping decisions
- Do not use this skill as the primary source for:
  - JPA-specific ORM behavior
  - reactive relational persistence
  - low-level `JdbcTemplate` code without Spring Data JDBC semantics
