# Spring Data store-specific module selection

Open this reference when the ordinary repository-abstraction path in `SKILL.md` is not enough and the task depends on one store's persistence semantics.

## Store-specific module rule

Use store-specific repository guidance when the underlying persistence model changes the design materially.

- JPA: entity graphs, flush behavior, lazy loading, JPQL.
- JDBC: aggregate persistence without JPA session semantics.
- R2DBC: reactive relational access.
- Redis: key-space and cache-style patterns.

Keep the common `spring-data` skill focused on shared repository abstractions.

## Repository base types by store

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

If the application uses more than one store module, scope repositories explicitly.

```java
@Configuration
@EnableJpaRepositories(basePackages = "com.example.jpa")
@EnableMongoRepositories(basePackages = "com.example.mongo")
class DataConfig {
}
```

Do not let repository scanning guess across several store modules.

## Decision points

| Situation | Use |
| --- | --- |
| domain logic depends on JPA session behavior | JPA-specific repository guidance |
| aggregate persistence should stay explicit and simple | JDBC-style repository guidance |
| repository APIs must return `Mono` or `Flux` | reactive relational repository guidance |
| TTL or key-space behavior is central | Redis-specific repository guidance |
