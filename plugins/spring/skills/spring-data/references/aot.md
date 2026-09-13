# Spring Data AOT and native-image

Open this reference when the ordinary repository-abstraction path is not enough and the task depends on AOT or native-image repository details.

Use this branch only when Spring AOT is actually enabled for the application, whether the target is a native image or a JVM build that still uses AOT processing.

## What AOT changes

Spring Data AOT can contribute generated type and property accessors, repository source code, and repository metadata in JSON format.
Each contribution is enabled by default when AOT processing is enabled.

AOT decisions such as the database dialect can be frozen at build time, so build-time configuration must match the runtime deployment.

## Repository eligibility rule

AOT repositories are available only for imperative repository interfaces in modules that support them.
They can generate implementations for eligible derived, annotated, and named query methods known at build time.

For Spring Data JPA 4.1, the supported feature list includes:

- derived query methods, `@Query`, `@NativeQuery`, and named query methods
- stored procedure methods annotated with `@Procedure`
- `@Modifying` methods returning `void` or `int`
- `@QueryHints`
- pagination, `Slice`, `Stream`, and `Optional` return types
- sort query rewriting
- interface and DTO projections
- value expressions, with the reflective information needed to parse and evaluate them

JPA limitations include:

- Hibernate is required for AOT processing
- `QueryRewriter` must be a no-args class.
- `QueryRewriter` beans are not supported
- methods accepting `ScrollPosition`, including keyset pagination, are not supported
- custom collection return types are not supported
- dynamic projections are excluded as overly complex
- base-interface methods such as `CrudRepository`, Querydsl, and Query by Example methods are excluded because their implementations come from repository fragments

## Configuration shape

These are the Spring Data AOT configuration properties documented for generated accessors, repository source, and repository metadata:

```properties
spring.aot.data.accessors.enabled=true
spring.aot.data.accessors.include=com.example.customer.Customer
spring.aot.data.accessors.exclude=com.example.previous.PreviousCustomer
spring.aot.repositories.enabled=true
spring.aot.repositories.metadata.enabled=true
spring.aot.jpa.repositories.enabled=true
```

The module-specific repository property uses the `spring.aot.[module-name].repositories.enabled` form.
For JPA, that is `spring.aot.jpa.repositories.enabled`.
Repository metadata requires `spring.aot.repositories.enabled`.

Use `spring.aot.jpa.repositories.use-entitymanager=true` only when JPA type scanning is insufficient and direct `EntityManagerFactory` access is required during AOT processing.

## Generated output roots

AOT generation can emit repository source and metadata under the build's generated output roots:

```text
# Gradle
build/generated/aotSources/
build/generated/aotResources/

# Maven
target/spring-aot/main/sources
target/spring-aot/main/resources
```

Treat generated repository fragments and metadata as internal build output, not as an API surface to call directly.

## Decision points

| Situation | Guidance |
| --- | --- |
| JVM deployment without AOT enabled | stay on the ordinary repository path |
| JVM deployment with AOT enabled | review the target module's supported and excluded repository features |
| native-image target is required | review repository patterns for AOT compatibility |
| repository behavior depends on dynamic runtime tricks | expect exclusion or additional AOT work |

## Verification rule

Verify one AOT build emits the expected repository artifacts under the generated output root and that the application still starts with the same repository set enabled.

## Source

This feature list follows the official [Spring Data JPA 4.1 AOT reference](https://docs.spring.io/spring-data/jpa/reference/jpa/aot.html).
