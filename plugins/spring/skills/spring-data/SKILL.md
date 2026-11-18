---
metadata:
  reference:
    Spring Data Commons:
      - version: 4.1.1
        url:
          - https://repo.maven.apache.org/maven2/org/springframework/data/spring-data-commons/maven-metadata.xml
          - https://repo.maven.apache.org/maven2/org/springframework/data/spring-data-commons/4.1.1/spring-data-commons-4.1.1.pom
      - version: 4.2.0-M2
        url:
          - https://repo.maven.apache.org/maven2/org/springframework/data/spring-data-commons/maven-metadata.xml
          - https://repo.maven.apache.org/maven2/org/springframework/data/spring-data-commons/4.2.0-M2/spring-data-commons-4.2.0-M2.pom
    Spring Data BOM:
      version: 2026.0.1
      url:
        - https://repo.maven.apache.org/maven2/org/springframework/data/spring-data-bom/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/data/spring-data-bom/2026.0.1/spring-data-bom-2026.0.1.pom
    Spring Data Commons Guide:
      url: https://docs.spring.io/spring-data/commons/reference/index.html
    Spring Data JPA AOT:
      version: 4.1.1
      url: https://docs.spring.io/spring-data/jpa/reference/jpa/aot.html
name: spring-data
description: >-
  Use Spring Data for repositories, queries, projections, auditing, pagination, and store modules.
---

# Spring Data

## Boundaries

Use `spring-data` for repository abstraction, derived query methods, projections, auditing, paging, scrolling, Query by Example, custom repository extensions, and common Spring Data mapping patterns.

- Use store-specific guidance for store-dependent persistence models.
- Keep this skill focused on what is shared across Spring Data modules rather than store-specific query languages or transaction mechanics.
- The common examples assume imperative contracts; use store-specific guidance for reactive execution.

## Dependency baseline

Before recommending a new version, check each selected Maven artifact on Maven Central for its latest stable release compatible with the project's Boot and Framework lines.
Keep project BOM or version-catalog management and existing pins unless the task authorizes changing them.

Pick the store-specific starter or module that matches the chosen persistence technology and pair it with the matching test support.

```text
Choose one store module for the repository implementation path.

- JPA: spring-boot-starter-data-jpa
- JDBC: spring-boot-starter-data-jdbc
- R2DBC: spring-boot-starter-data-r2dbc
- MongoDB: spring-boot-starter-data-mongodb
- Redis: spring-boot-starter-data-redis
```

If the project imports the Spring Data BOM, omit child Spring Data versions and use the BOM as the version anchor.

Spring Data Commons 4.1.1 is the documented stable example baseline.
Version 4.2.0-M2 is a preview milestone, not a stable release.

## Repository configuration

For a property known at compile time, prefer type-safe property references on Spring Data Commons 4.1; retain string paths for names known only at runtime.
Use `@ProjectedPayload` only for externally bound web projections, not repository return projections.
A dynamic projection must be a valid projection interface or DTO; class-based DTOs need one constructor or an explicitly selected `@PersistenceCreator`, and declared query results must match the projection.
For Commons 4.0+, use package-level `@NullMarked` and annotate nullable exceptions; use `Optional<T>` for absent aggregates and never annotate the `Optional` itself nullable.
For repository, projection, nullability, scrolling, or test-slice setup, read [repository configuration](references/repository-configuration.md).

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

## Query and auditing decisions

Use declared queries when derived names become unwieldy, keeping query language and advanced attributes store-specific.
Reserved `findById`, `existsById`, and `deleteById` target the declared identifier, not an unrelated property named `id`.
Choose `Page` for totals with its separate count, `Slice` for forward paging without a count, and `Window` for offset or keyset scrolling with a carried position.
For keyset scrolling, project positioning columns, avoid null keys, carry the previous `ScrollPosition`, and use a deterministic sort.
Enable auditing per store; use `AuditorAware<T>` for imperative or `ReactiveAuditorAware<T>` for reactive auditor fields.
For examples of queries, projections, paging, scrolling, fragments, callbacks, or auditing, read [repository, query, and auditing examples](references/repository-query-and-auditing-examples.md).

## Testing checklist

Check the changed repository operation against the configured store.
Use the existing native test setup.

- Verify repository methods map to the intended query behavior.
- Verify projections expose only the fields callers need.
- Verify null handling matches the repository contract and caller expectation.
- Verify paging and sorting behavior matches the repository contract.
- Verify scrolling or windowed retrieval only when the caller really needs incremental traversal.
- Verify auditing fields populate only when auditing is enabled and expected.
- Verify the chosen test slice reflects the actual store module in use.

## Production checklist

- Keep repository contracts and derived query names stable for existing callers.
- Avoid leaking store-specific behavior into common repository abstractions unless it is intentional.
- Use projections and paging deliberately to control query shape and result size.
- Keep custom repository fragments, callbacks, and conversions reviewable because they change shared persistence behavior.
- Keep auditing semantics explicit and reviewable.
- Treat repository slice tests as part of the persistence compatibility surface.

## References

- Open [store specific module selection](references/store-specific-module-selection.md) when the ordinary repository-abstraction path is not enough and the task depends on one store's persistence semantics.
- Open [query by example](references/query-by-example.md) when the blocker is Query by Example matcher behavior, probe design, or choosing QBE instead of a derived query.
- Open [entity callbacks and conversions](references/entity-callbacks-and-conversions.md) when the blocker is entity callback registration, custom conversions, or per-property value conversion.
- Open [multimodule repository scanning](references/multimodule-repository-scanning.md) when the blocker is strict repository scanning across more than one Spring Data store module.
- Open [domain events](references/domain-events.md) when the blocker is aggregate-root domain event publication through Spring Data repositories.
- Open [rest exposure](references/rest-exposure.md) when the task is specifically about exposing repositories as HTTP resources.
- Open [aot](references/aot.md) when the task depends on AOT or native-image repository details.
- Open [scrolling patterns](references/scrolling-patterns.md) for offset or keyset positions, `WindowIterator`, or keyset projection constraints.
- Open [jpa transactions](references/jpa-transactions.md) for transaction inheritance, declared queries, `@Modifying`, or facade transaction boundaries.
