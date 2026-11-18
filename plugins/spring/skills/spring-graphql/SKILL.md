---
metadata:
  reference:
    Spring GraphQL:
      version: 2.0.5
      url:
        - https://docs.spring.io/spring-graphql/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/graphql/spring-graphql/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/graphql/spring-graphql/2.0.5/spring-graphql-2.0.5.pom
    Spring Boot Dependency BOM:
      version: 4.1.1
      url:
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom
name: spring-graphql
description: >-
  Use Spring GraphQL for schemas, resolvers, batching, subscriptions, transports, and error handling.
---

# Spring for GraphQL

## Purpose

Keep GraphQL schema, resolver, transport, batching, and tester decisions at the API boundary while domain services stay independent of GraphQL annotations and response shapes.

- Use narrower guidance for ordinary REST or non-GraphQL HTTP endpoint design.
- Keep federation out of the ordinary path.
  - Open the federation reference only when the graph is intentionally split across several services.

## Surface map

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| Ordinary schema-first query and mutation API | one service owns the schema and HTTP transport is enough | stay in `SKILL.md` |
| Subscription or alternate transports | using subscriptions, WebSocket, SSE, or RSocket | open [references/transports-and-subscriptions.md](references/transports-and-subscriptions.md) |
| Custom batching lifecycle | `@BatchMapping` is not enough | open [references/advanced-dataloader.md](references/advanced-dataloader.md) |
| Test-slice or integration-tester choice | choosing the GraphQL test surface | open [references/testing-graphql.md](references/testing-graphql.md) |
| Security context or explicit error shaping | working with request metadata, authorization, or stable error categories | open [references/security-context-and-errors.md](references/security-context-and-errors.md) |
| Federated graph ownership | more than one service contributes to the graph | open [references/federation.md](references/federation.md) |

## Dependency baseline

Before recommending a new version, check each selected Maven artifact on Maven Central for its latest stable release compatible with the project's Boot and Framework lines.
Keep project BOM or version-catalog management and existing pins unless the task authorizes changing them.

Spring GraphQL 2.0.x requires Spring Framework 7.0, graphql-java 25.0, and Java 17+.
Spring Boot 4.1.1 manages Spring GraphQL 2.0.5.
Jackson 3.x is the default serialization library, with Jackson 2.x supported as a fallback.

Use the Boot starter for application code and the GraphQL test module for focused tests.

For dependency baseline, see [example 1](references/configuration-examples.md#example-1-dependency-baseline).

For transport-specific dependency selection, read [transport artifacts](references/transport-artifacts.md).

## HTTP configuration

For schema resources, HTTP endpoint, GraphiQL, schema printer, browser CORS, introspection setup, or focused Maven and Gradle test commands, read [HTTP configuration](references/http-configuration.md).
Start with one schema resource set and HTTP endpoint; add WebSocket, SSE, or RSocket only when needed.
GraphiQL is disabled by default, auto-enabled with devtools, and defaults to `/graphiql`; introspection is enabled by default.
Expose the schema printer only when needed.

## Build and run path

```sh
./mvnw spring-boot:run
```

```sh
./gradlew bootRun
```

Run only the transport path needed for the task.
Use focused resolver tests when a live server adds no evidence.

## Mapping and batching decisions

| Situation | Use |
| --- | --- |
| Root query or mutation field | `@QueryMapping` or `@MutationMapping` |
| Nested field resolved from one parent object | `@SchemaMapping` |
| Nested field resolved for many parents at once | `@BatchMapping` |
| Custom loader options, several loader keys, or shared batch lifecycle | dedicated DataLoader registration |

Prefer `@BatchMapping` when one nested field is the obvious batching boundary.
Keep manual DataLoader registration for cases where batching must be shared across several resolvers or keyed differently from the schema field itself.

### Spring Data repository resolvers

Spring Data repositories annotated with `@GraphQlRepository` and extending `QuerydslPredicateExecutor` or `QueryByExampleExecutor` are auto-detected as `DataFetcher` candidates for matching top-level queries.
Use this when the query shape maps directly to a repository method and no additional controller logic is needed.

## Coding procedure

1. Keep schema field names and nullability intentional because clients depend on them directly.
2. Use annotated controllers for ordinary query, mutation, and field mappings instead of low-level wiring when the codebase already follows Spring conventions.
3. Return domain data mapped to schema-facing DTOs or stable shapes instead of leaking persistence entities casually.
4. Prefer `@BatchMapping` first, then reach for explicit DataLoader registration when batching needs custom loader composition.
5. Keep authorization and context propagation explicit at the GraphQL boundary.
6. Make validation, authorization, and execution failures observable as distinct GraphQL error categories.
7. For API changes, test successful execution and affected validation or authorization failure paths.

## Spring GraphQL 2.0 features

When using 2.0 nullability inspection, argument binding, partial updates, Kotlin extensions, or SSE, read [2.0 features](references/version-2-features.md).

## Implementation examples

For schema, batching, DataLoader, errors, context, or tests, read [implementation examples](references/implementation-examples.md).

## Testing slice choice

| Situation | Use |
| --- | --- |
| Controller-focused GraphQL mapping test | `@GraphQlTest` + `GraphQlTester` |
| Server integration with interceptor chain | `HttpGraphQlTester` (auto-configured with `@SpringBootTest` on random/defined port; use `@AutoConfigureHttpGraphQlTester` for MOCK environment) |
| Subscription verification | `GraphQlTester` + reactive assertions |

Open the testing reference when choosing transport, testing subscriptions, or configuring test slices or integration tests.

For query paths, endpoint paths, or field mapping examples, read [output shapes](references/output-shapes.md).

## Testing checklist

Exercise the affected field through its schema, resolver, and access rules.

- Verify the schema field names and resolver paths match what the client queries.
- Verify GraphQlTester assertions cover both data paths and expected errors.
- Verify nested field resolution does not accidentally create N+1 access patterns.
- Verify authorization or validation failures surface as GraphQL errors in the intended shape.
- Verify the chosen transport path stays aligned with deployment configuration.

## Failure classification

- Treat schema-validation, field-selection, and input-shape problems as client contract failures.
- Treat authorization denials and missing context propagation as security-boundary failures.
- Treat resolver exceptions, DataLoader misconfiguration, and transport wiring failures as execution or infrastructure failures.

## Production checklist

- Keep schema field names, nullability, and argument names stable after clients depend on them.
- Add batching where nested resolution would otherwise create avoidable query explosions.
- Keep GraphQL error shapes intentional so client handling stays predictable.
- Do not expose subscriptions or alternate transports unless clients really need them.
- Treat GraphQL schema and tester assertions as part of the API compatibility surface.
