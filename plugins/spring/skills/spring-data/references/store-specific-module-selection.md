# Spring Data store-specific module selection

Open this reference when the ordinary repository-abstraction path in [SKILL.md](../SKILL.md) is not enough and the task depends on one store's persistence semantics.

## Store-specific module rule

Use store-specific repository guidance when the underlying persistence model changes the design materially.

- JPA: entity graphs, flush behavior, lazy loading, JPQL.
- JDBC: aggregate persistence without JPA session semantics.
- R2DBC: reactive relational access.
- MongoDB: document storage, nested aggregates, and flexible document shape.
- Redis: key-space and cache-style patterns.

Keep the common `spring-data` skill focused on shared repository abstractions.

If the project already imports the Spring Data BOM for the chosen release train, omit child Spring Data versions from the store-specific dependency examples and keep the BOM as the single version anchor.

## Representative repository base types

The examples below are illustrative, not exhaustive. Use the base type that matches the selected store and programming model.

Choose the repository base interface that matches the selected store.

```java
interface JpaOrderRepository extends JpaRepository<Order, Long> {
}

interface JdbcOrderRepository extends ListCrudRepository<Order, Long> {
}

interface ReactiveOrderRepository extends ReactiveCrudRepository<Order, Long> {
}
```

Use `JpaRepository` only when the task actually relies on JPA semantics such as dirty checking, entity graphs, or JPQL.

## Store-selection decision table

| Store | Choose it when | Avoid it when |
| --- | --- | --- |
| JPA | lazy loading, JPQL, entity graphs, persistence context semantics matter | you want direct aggregate persistence without session state |
| JDBC | aggregate persistence is simple and explicit SQL-backed mapping is enough | you need JPA session or query-language features |
| R2DBC | the repository path must stay reactive end to end | the app is fundamentally blocking |
| MongoDB | the aggregate shape is document-oriented | strong relational joins are central |
| Redis | key-value, cache-like, TTL-driven persistence matters | relational consistency is the main problem |

## Multiple modules in one application

Open [multimodule-repository-scanning.md](multimodule-repository-scanning.md) when the application uses more than one store module and repository scanning must be scoped explicitly.

## Decision points

| Situation | Use |
| --- | --- |
| domain logic depends on JPA session behavior | JPA-specific repository guidance |
| aggregate persistence should stay explicit and simple | JDBC-style repository guidance |
| repository APIs must return `Mono` or `Flux` | reactive relational repository guidance |
| TTL or key-space behavior is central | Redis-specific repository guidance |
