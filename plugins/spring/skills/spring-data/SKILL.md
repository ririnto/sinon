---
name: "spring-data"
description: "Use this skill when designing Spring Data repositories, derived queries, projections, auditing, paging, and repository-based persistence patterns that span multiple Spring Data modules."
metadata:
  title: "Spring Data"
  official_project_url: "https://spring.io/projects/spring-data"
  reference_doc_urls:
    - "https://docs.spring.io/spring-data/commons/reference/index.html"
    - "https://docs.spring.io/spring-data/jpa/reference/index.html"
    - "https://docs.spring.io/spring-data/mongodb/reference/index.html"
    - "https://docs.spring.io/spring-data/redis/reference/index.html"
    - "https://docs.spring.io/spring-data/jdbc/reference/index.html"
    - "https://docs.spring.io/spring-data/r2dbc/reference/index.html"
    - "https://docs.spring.io/spring-data/rest/reference/index.html"
  release_train: "2025.1.0"
  version: "4.0.5"
---

Use this skill when designing Spring Data repositories, derived queries, projections, auditing, paging, scrolling, null-safe repository contracts, and repository-based persistence patterns that span multiple Spring Data modules.

## Boundaries

Use `spring-data` for repository abstraction, derived query methods, projections, auditing, paging, scrolling, Query by Example, custom repository extensions, and common Spring Data mapping patterns.

- Use store-specific repository guidance when the real problem depends on one store's persistence model.
- Keep this skill focused on what is shared across Spring Data modules rather than store-specific query languages or transaction mechanics.

## Common path

The ordinary Spring Data job is:

1. Start from the repository boundary and the aggregate or document shape the application needs.
2. Use the smallest repository abstraction that fits the use case.
3. Prefer derived queries and projections for straightforward access paths.
4. Keep null handling, paging, and scrolling explicit in repository contracts.
5. Add auditing and mapping callbacks only where the domain actually benefits from them.
6. Add a repository-focused test slice that proves the query and mapping behavior.

## Dependency baseline

Pick the store-specific starter or module that matches the chosen persistence technology and pair it with the matching test support.

```text
Choose one store module for the repository implementation path.

- JPA: spring-boot-starter-data-jpa
- JDBC: spring-boot-starter-data-jdbc
- R2DBC: spring-boot-starter-data-r2dbc
- MongoDB: spring-boot-starter-data-mongodb
- Redis: spring-boot-starter-data-redis
```

Keep `spring-data` itself focused on repository abstractions shared across those modules.

## First safe configuration

### Repository abstraction shape

```java
interface CustomerRepository extends ListCrudRepository<Customer, Long> {
}
```

### Derived query shape

```java
Optional<Customer> findByEmailIgnoreCase(String email)
```

### Projection shape

```java
record CustomerView(Long id, String email) {
}
```

### Null-safe repository contract shape

```java
Optional<Customer> findByEmailIgnoreCase(String email)
```

### Scroll or window shape

```java
Window<CustomerView> findTop20ByAddressCity(String city, ScrollPosition position)
```

## Coding procedure

1. Keep repository interfaces close to the aggregate or document they serve.
2. Use derived query methods for simple lookup paths before introducing custom query strings.
3. Use projections when callers do not need the full aggregate shape.
4. Keep null handling explicit with `Optional`, collections, slices, pages, or windows that match the real contract.
5. Use custom repository fragments only when the shared repository abstraction is no longer enough.
6. Enable auditing only when created or modified metadata is part of the real model.
7. Keep paging, sorting, and scrolling explicit in repository methods that expose them.
8. Keep shared mapping callbacks and conversions intentional rather than hidden in store-specific infrastructure.
9. Test the repository behavior with the narrowest matching test slice.

## Implementation examples

### Repository abstraction

```java
interface CustomerRepository extends ListCrudRepository<Customer, Long> {
    Optional<Customer> findByEmailIgnoreCase(String email);
    Slice<CustomerView> findByAddressCity(String city, Pageable pageable);
    Window<CustomerView> findTop20ByAddressCity(String city, ScrollPosition position);
}

record CustomerView(Long id, String email) {
}
```

### Custom repository fragment

```java
interface CustomerRepositoryCustom {
    List<CustomerView> findRecentlyActiveCustomers(Instant since);
}

interface CustomerRepository extends ListCrudRepository<Customer, Long>, CustomerRepositoryCustom {
}
```

### Auditing

```java
class PurchaseOrder {
    @CreatedDate
    Instant createdAt;

    @LastModifiedDate
    Instant updatedAt;
}
```

Enable the matching store-specific auditing configuration in the store-specific path rather than assuming JPA.

### Query by Example shape

```java
ExampleMatcher matcher = ExampleMatcher.matching()
    .withIgnoreCase()
    .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING);

Example<Customer> probe = Example.of(new Customer("a@example.com", null), matcher);
```

### Callback or conversion boundary

Shared mapping callbacks and conversions belong to the common Spring Data model, but keep store-specific callback mechanics in the store-specific path.

## Output and configuration shapes

### Derived query shape

```text
findByEmailIgnoreCase
findByAddressCity
```

### Paging shape

```java
Slice<CustomerView> findByAddressCity(String city, Pageable pageable)
```

### Auditing field shape

```java
@CreatedDate
Instant createdAt;
```

## Testing checklist

- Verify repository methods map to the intended query behavior.
- Verify projections expose only the fields callers need.
- Verify null handling matches the repository contract and caller expectation.
- Verify paging and sorting behavior matches the repository contract.
- Verify scrolling or windowed retrieval only when the caller really needs incremental traversal.
- Verify auditing fields populate only when auditing is enabled and expected.
- Verify the chosen test slice reflects the actual store module in use.

## Production checklist

- Keep repository contracts and derived query names stable after callers depend on them.
- Avoid leaking store-specific behavior into common repository abstractions unless it is intentional.
- Use projections and paging deliberately to control query shape and result size.
- Keep custom repository fragments, callbacks, and conversions reviewable because they change shared persistence behavior.
- Keep auditing semantics explicit and reviewable.
- Treat repository slice tests as part of the persistence compatibility surface.

## References

- Open [references/store-specific-module-selection.md](references/store-specific-module-selection.md) when the ordinary repository-abstraction path is not enough and the task depends on one store's persistence semantics.
- Open [references/query-by-example.md](references/query-by-example.md) when the blocker is Query by Example matcher behavior, probe design, or choosing QBE instead of a derived query.
- Open [references/entity-callbacks-and-conversions.md](references/entity-callbacks-and-conversions.md) when the blocker is entity callback registration, custom conversions, or per-property value conversion.
- Open [references/multimodule-repository-scanning.md](references/multimodule-repository-scanning.md) when the blocker is strict repository scanning across more than one Spring Data store module.
- Open [references/spring-data-domain-events.md](references/spring-data-domain-events.md) when the blocker is aggregate-root domain event publication through Spring Data repositories.
- Open [references/spring-data-rest-exposure.md](references/spring-data-rest-exposure.md) when the task is specifically about exposing repositories as HTTP resources.
- Open [references/spring-data-aot.md](references/spring-data-aot.md) when the task depends on AOT or native-image repository details.
