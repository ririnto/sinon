---
name: spring-data
description: >-
  Design Spring Data repositories, derived queries, projections, auditing, paging, and scrolling across multiple Spring Data modules.
  Use when writing derived or `@Query` repository methods, choosing between JPA, MongoDB, Redis, JDBC, or R2DBC modules, configuring auditing, or implementing projections and DTO mappings.
---

# Spring Data

## Boundaries

Use `spring-data` for repository abstraction, derived query methods, projections, auditing, paging, scrolling, Query by Example, custom repository extensions, and common Spring Data mapping patterns.

- Use store-specific repository guidance when the real problem depends on one store's persistence model.
- Keep this skill focused on what is shared across Spring Data modules rather than store-specific query languages or transaction mechanics.
- The concrete repository examples in this common path assume imperative repository contracts; reactive execution details belong in the matching store-specific reactive path.

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

If the project already imports the Spring Data BOM for this release train, omit child Spring Data versions from dependency examples and keep the BOM import as the single version anchor.

The current stable Spring Data release train is `2026.0.0` with Commons `4.1.0`.

## First safe configuration

### Repository abstraction shape

```java
interface CustomerRepository extends ListCrudRepository<Customer, Long> {
}
```

### Type-safe property paths

Spring Data Commons 4.1 introduces type-safe property path references.
Prefer method references over string-based property names when the property is known at compile time.

```java
Sort.by(Person::getFirstName, Person::getLastName)
```

Nested paths compose through chaining:

```java
TypedPropertyPath.of(Person::getAddress).then(Address::getCountry)
```

Keep string-based `PropertyPath.from("name", Person.class)` only when the property name is not known at compile time.

### Derived query shape

```java
Optional<Customer> findByEmailIgnoreCase(String email);
```

### Projection shape

Use `@ProjectedPayload` only when a projection is bound from an external web payload, such as controller method parameters.
Repository return projections do not require this annotation.

```java
@ProjectedPayload
interface CustomerView {
    String getEmail();
}
```

Record-based DTO projections used as repository return types do not require `@ProjectedPayload`.

```java
record CustomerView(Long id, String email) {
}
```

### Dynamic projection shape

Use `Class<T>` projection parameter when callers choose the projection shape at runtime:

```java
record CustomerSummary(String email) {
}

<T> List<T> findByEmail(String email, Class<T> projectionType);
```

Callers pass the projection class:

```java
List<CustomerView> views = repository.findByEmail("a@example.com", CustomerView.class);
List<CustomerSummary> summaries = repository.findByEmail("a@example.com", CustomerSummary.class);
```

The projection type must be a valid Spring Data projection interface or a DTO class such as a record.
For class-based DTO projections, keep a single constructor or mark the constructor Spring Data should use with `@PersistenceCreator`.
Dynamic projection works naturally with derived queries; declared queries must still return a shape that matches the selected projection.

### Null-safe repository contract shape

```java
Optional<Customer> findByEmailIgnoreCase(String email);
```

### JSpecify nullability in repository contracts

Spring Data Commons 4.0 and later align with JSpecify-style nullability.
Use package-level `@NullMarked` as the default, then mark only the nullable exceptions explicitly:

| Annotation | Meaning | Typical use |
| --- | --- | --- |
| `@NullMarked` | types in the package are non-null by default | `package-info.java` |
| `@Nullable` | this parameter or type usage may be null | optional parameters, nullable return types |
| `@NullUnmarked` | temporarily opt one declaration out of `@NullMarked` | migration or mixed nullness areas |

```java
@NullMarked
package com.example.customer;

import org.jspecify.annotations.NullMarked;
```

```java
interface CustomerRepository extends ListCrudRepository<Customer, Long> {
    Optional<Customer> findByEmail(@Nullable String email);

    @Nullable
    Customer findPrimaryContactByAccountId(Long accountId);
}
```

Use `Optional<T>` for absent aggregate results and `@Nullable` for truly nullable scalar or entity return types.
Do not wrap `Optional<T>` itself in `@Nullable`; absence belongs inside the `Optional`.

### Scroll or window shape

```java
Window<CustomerView> findFirst20ByAddressCityOrderByIdAsc(String city, ScrollPosition position);
```

### Repository test slice shape

```java
@DataJpaTest
class CustomerRepositoryTests {
    @Autowired
    CustomerRepository repository;

    @Test
    void findsProjectionByEmail() {
        List<CustomerView> views = repository.findByEmail("a@example.com", CustomerView.class);
        assertThat(views).extracting(CustomerView::email).contains("a@example.com");
    }
}
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
    Window<CustomerView> findFirst20ByAddressCityOrderByIdAsc(String city, ScrollPosition position);
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

### @Query custom query shape

Use declared queries when derived query method names become unwieldy.
The annotation name is shared across Spring Data modules, but the actual query language and advanced attributes stay store-specific.

```java
@Query("select c from Customer c where c.active = true")
List<Customer> findActiveCustomers();
```

Keep common guidance at the level of 'declared query versus derived query'.
Move JPQL, native SQL, SpEL, and store-specific `@Query` attributes to the store-specific path.

### Derived query method-name pitfalls

Spring Data reserves identifier-targeting base method names.
When domain properties happen to collide with them, the behavior can surprise:

| Pattern | Behavior | Pitfall |
| --- | --- | --- |
| `findById` | maps to identifier equality | `Id` means the declared identifier property, not a random field named `id` |
| `existsById` | checks identifier existence | does not derive a custom predicate from another `id`-like field |
| `deleteById` | deletes by identifier | does not derive a custom delete predicate from another `id`-like field |

```java
class Customer {
    @Id Long pk;
    Long id;
}

Optional<Customer> findById(Long id);
Optional<Customer> findCustomerById(Long id);
```

`findById` targets the declared identifier property, while `findCustomerById` targets the property named `id`.
Prefer explicit derived query names when a domain property could be confused with the identifier.

### Sort null handling

Spring Data Commons 4.1 adds `nullHandling` to `@SortDefault` and allows configuring default null-handling behavior for `Sort` resolution:

```java
Page<Customer> findAll(@SortDefault(sort = "lastname", nullHandling = Sort.NullHandling.NULLS_LAST) Pageable pageable);
```

### Paging and scrolling shapes

Choose the return type that matches what callers actually need:

| Type | Use when | Trims result |
| --- | --- | --- |
| `Page` | callers need total count and total pages | a separate count operation runs |
| `Slice` | callers navigate forward through unknown total size | no count query; next-page existence is available |
| `Window` | callers need scroll-based iteration and a window they can extract the next position from | can be offset- or keyset-backed |

```java
Page<CustomerView> findByAddressCity(String city, Pageable pageable);
Slice<CustomerView> findByAddressCity(String city, Pageable pageable);
Window<CustomerView> findFirst20ByAddressCityOrderByIdAsc(String city, ScrollPosition position);
```

Use `Page` when the caller needs total count, `Slice` when the caller scrolls through an unknown-length feed, and `Window` when the caller scrolls with deterministic ordering and extracts the next position from the current window.
`Page` is the heaviest because it runs a separate count query.
`Slice` avoids the count but still uses pageable offset traversal.
`Window` represents scroll-based iteration, and callers extract the next `ScrollPosition` from the current window with `window.positionAt(...)`; the underlying scroll can be offset- or keyset-based depending on the repository method and store support.

Keyset scrolling constraints:

- The query must project the keyset columns used for positioning.
- Null keys in the keyset column make row positioning unstable.
- The `ScrollPosition` must be carried forward from the previous Window result.
- Keep a deterministic sort such as `OrderByIdAsc` so every window position stays stable.

Open [references/scrolling`-patterns.md`](references/scrolling-patterns.md) when the blocker is `WindowIterator`, offset-versus-keyset `ScrollPosition`, or why a projection breaks keyset scrolling.

### Auditing activation

Enable auditing per store module.
The entity annotations are shared, but the activation mechanism is store-specific and belongs in the store-specific path.

Auditing fields:

```java
class PurchaseOrder {
    @CreatedDate
    Instant createdAt;

    @LastModifiedDate
    Instant updatedAt;

    @CreatedBy
    String createdBy;

    @LastModifiedBy
    String modifiedBy;
}
```

If using `@CreatedBy` or `@LastModifiedBy`, provide the auditor SPI that matches the store style: `AuditorAware<T>` for imperative repositories and `ReactiveAuditorAware<T>` for reactive infrastructure.

Open [references/jpa`-transactions.md`](references/jpa-transactions.md) when the blocker is declared-query transaction behavior, `@Modifying`, or a facade-level transaction boundary in a JPA store.

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

- Open [references/store`-specific-module-selection.md`](references/store-specific-module-selection.md) when the ordinary repository-abstraction path is not enough and the task depends on one store's persistence semantics.
- Open [references/query`-by-example.md`](references/query-by-example.md) when the blocker is Query by Example matcher behavior, probe design, or choosing QBE instead of a derived query.
- Open [references/entity`-callbacks-and-conversions.md`](references/entity-callbacks-and-conversions.md) when the blocker is entity callback registration, custom conversions, or per-property value conversion.
- Open [references/multimodule`-repository-scanning.md`](references/multimodule-repository-scanning.md) when the blocker is strict repository scanning across more than one Spring Data store module.
- Open [references/spring`-data-domain-events.md`](references/spring-data-domain-events.md) when the blocker is aggregate-root domain event publication through Spring Data repositories.
- Open [references/spring`-data-rest-exposure.md`](references/spring-data-rest-exposure.md) when the task is specifically about exposing repositories as HTTP resources.
- Open [references/spring`-data-aot.md`](references/spring-data-aot.md) when the task depends on AOT or native-image repository details.
- Open [references/scrolling`-patterns.md`](references/scrolling-patterns.md) when the blocker is scroll position semantics, `WindowIterator`, or projection constraints in keyset scrolling.
- Open [references/jpa`-transactions.md`](references/jpa-transactions.md) when the blocker is JPA repository transaction inheritance, declared `@Query` methods, or `@Modifying` behavior.
