---
name: spring-data-r2dbc
description: >-
  Use this skill when the user asks to "use Spring Data R2DBC", "build a reactive repository", "choose R2DBC over JPA or JDBC", or needs guidance on reactive relational persistence with Spring Data R2DBC.
---

# Spring Data R2DBC

## Overview

Use this skill to design reactive relational persistence with Spring Data R2DBC around non-blocking repository contracts and stream-friendly data access. The common case is one reactive aggregate repository with return types that honestly match the data shape. Choose R2DBC only when the application boundary is already reactive or must stay non-blocking end to end.

## Use This Skill When

- You are designing reactive repositories.
- You need to choose R2DBC over JDBC or JPA.
- You need a default Spring Data R2DBC repository shape.
- Do not use this skill when the application boundary is blocking and does not need reactive relational access.

## Common-Case Workflow

1. Confirm that the application boundary is already reactive or should be.
2. Use `Mono` or `Flux` repository contracts that match the actual data shape.
3. Keep blocking persistence APIs out of the flow.
4. Choose R2DBC when end-to-end non-blocking behavior matters.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-r2dbc</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one reactive entity and repository:

```java
@Table("products")
public class ProductEntity {
    @Id
    Long id;
    String name;
}

interface ProductRepository extends ReactiveCrudRepository<ProductEntity, Long> {
    Flux<ProductEntity> findByNameContaining(String name);
}
```

---

*Applies when:* the service boundary is reactive and relational persistence must stay non-blocking.

## Ready-to-Adapt Templates

Reactive repository:

```java
interface ProductRepository extends ReactiveCrudRepository<ProductEntity, Long> {
    Flux<ProductEntity> findByNameContaining(String name);
}
```

---

*Applies when:* the repository needs one-result and many-result reactive methods.

Reactive service:

```java
@Service
class ProductService {
    private final ProductRepository productRepository;

    ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    Mono<ProductEntity> findOne(Long id) {
        return productRepository.findById(id);
    }
}
```

---

*Applies when:* the surrounding application flow is already reactive.

Streaming query:

```java
Flux<ProductEntity> findByNameContaining(String name);
```

---

*Applies when:* the repository query can produce several items and the caller consumes a stream.

## Validate the Result

Validate the common case with these checks:

- repository return types match the real data shape
- blocking JPA or JDBC APIs are not mixed into the reactive flow
- the application boundary really benefits from non-blocking relational access
- reactive persistence remains readable instead of wrapping blocking semantics in reactive types

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| reactive repository boundaries and non-blocking persistence heuristics | `./references/r2dbc-patterns.md` |

## Invariants

- MUST keep blocking persistence APIs out of reactive flows.
- SHOULD use R2DBC only when reactive boundaries are real.
- MUST match repository return types to the actual data shape.
- SHOULD keep reactive persistence readable and minimal.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| mixing blocking JPA or JDBC calls into a reactive service path | the non-blocking boundary becomes false | keep the persistence stack consistently reactive |
| choosing R2DBC without a reactive web or messaging boundary | the extra complexity buys little | use blocking persistence unless the end-to-end boundary is reactive |
| returning `Flux` or `Mono` types that do not match the real contract | the repository API becomes misleading | align return types to actual result cardinality |

## Scope Boundaries

- Activate this skill for:
  - reactive relational persistence
  - `Mono` / `Flux` repository contracts
  - non-blocking data access design
- Do not use this skill as the primary source for:
  - blocking repository design
  - generic WebFlux API design without persistence focus
