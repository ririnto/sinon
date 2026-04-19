# Spring Data Query by Example

Open this reference when the blocker is Query by Example matcher behavior, probe design, or deciding whether QBE fits better than a derived query method.

## QBE fit blocker

Use QBE when the caller already has a probe object and the query shape is mostly equality or matcher-driven filtering rather than a stable named repository contract.

```java
interface CustomerRepository extends ListCrudRepository<Customer, Long>, QueryByExampleExecutor<Customer> {
}
```

Prefer a derived query when the lookup path is stable enough to deserve a named repository method.

## Matcher design blocker

```java
ExampleMatcher matcher = ExampleMatcher.matching()
    .withIgnoreCase()
    .withStringMatcher(ExampleMatcher.StringMatcher.CONTAINING)
    .withIgnoreNullValues();

Example<Customer> example = Example.of(new Customer("a@example.com", null), matcher);
```

Keep matcher rules explicit so probe null handling and string matching semantics stay reviewable.

## Decision points

| Situation | First check |
| --- | --- |
| Caller already builds a filter object | use QBE if the matcher rules stay simple |
| Query path is stable and frequently reused | prefer a derived repository method |
| Null properties behave unexpectedly | verify `ExampleMatcher` null-handling rules |
| QBE needs store-specific query semantics | move to the store-specific path |
