# Repository, query, and auditing examples

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
`Window` represents scroll-based iteration, and callers extract the next `ScrollPosition` from the current window with `window.positionAt(...)`.
The underlying scroll can be offset- or keyset-based depending on the repository method and store support.

Keyset scrolling constraints:

- The query must project the keyset columns used for positioning.
- Null keys in the keyset column make row positioning unstable.
- The `ScrollPosition` must be carried forward from the previous Window result.
- Keep a deterministic sort such as `OrderByIdAsc` so every window position stays stable.

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

### Auditing field shape

```java
@CreatedDate
Instant createdAt;
```
