# Repository configuration

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
Sort.by(
    TypedPropertyPath.of(Person::getFirstName),
    TypedPropertyPath.of(Person::getLastName)
)
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

interface CustomerRepository extends ListCrudRepository<Customer, Long> {
    <T> List<T> findByEmail(String email, Class<T> projectionType);
}
```

Callers pass the projection class:

```java
List<CustomerView> views = repository.findByEmail("a@example.com", CustomerView.class);
List<CustomerSummary> summaries = repository.findByEmail("a@example.com", CustomerSummary.class);
```

The projection type must be a valid Spring Data projection interface or a DTO class such as a record.
For class-based DTO projections, keep a single constructor or mark the constructor Spring Data should use with `@PersistenceCreator`.
Dynamic projection works naturally with derived queries.
Declared queries must still return a shape that matches the selected projection.

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
Do not wrap `Optional<T>` itself in `@Nullable`.
Absence belongs inside the `Optional`.

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
