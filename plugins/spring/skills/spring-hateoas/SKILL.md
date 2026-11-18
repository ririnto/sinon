---
metadata:
  reference:
    Spring HATEOAS:
      version: 3.1.2
      url:
        - https://docs.spring.io/spring-hateoas/docs/current/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/hateoas/spring-hateoas/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/hateoas/spring-hateoas/3.1.2/spring-hateoas-3.1.2.pom
    Spring Boot 4.1:
      version: 4.1.1
      url:
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom
name: spring-hateoas
description: >-
  Use Spring HATEOAS for links, representations, assemblers, HAL-FORMS, and page navigation.
---

# Spring HATEOAS

## Boundaries

Use `spring-hateoas` for link modeling, representation assembly, hypermedia media types, and affordance exposure on the server side.

Keep controllers thin; put hypermedia shape in assemblers or processors.
Use narrower guidance for API documentation from tests.

## Surface map

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| Plain HAL item and collection responses | one controller returns `EntityModel` or `CollectionModel` | stay in `SKILL.md` |
| Page navigation and page metadata | clients depend on `prev`/`next`/`first`/`last` or strict page metadata semantics | open [references/pagedmodel-navigation-and-page-metadata.md](references/pagedmodel-navigation-and-page-metadata.md) |
| HAL-FORMS or affordances | clients need action metadata or `_templates` | open [references/hal-forms-and-affordances.md](references/hal-forms-and-affordances.md) |
| Aggregate-type links or explicit hypermedia activation | `EntityLinks`, `@ExposesResourceFor`, or `@EnableHypermediaSupport` is the blocker | open [references/entity-links-and-hypermedia-support.md](references/entity-links-and-hypermedia-support.md) |
| Cross-cutting link enrichment | one shared rule must enrich many models after assembly | open [references/representation-processors.md](references/representation-processors.md) |
| Reverse-proxy link correctness | generated host, scheme, or base path is wrong | open [references/forwarded-headers-and-proxy-configuration.md](references/forwarded-headers-and-proxy-configuration.md) |
| Problem Details error payloads | the error path needs `application/problem+json` | open [references/problem-details-error-representations.md](references/problem-details-error-representations.md) |

## Representation decisions

| Situation | Use |
| --- | --- |
| One item with links | `EntityModel<T>` |
| Collection with top-level links | `CollectionModel<T>` |
| Page result with navigation metadata | `PagedModel<T>` |
| Empty collection or empty page | `CollectionModel.empty()` or `PagedModel.empty()` |

Keep link relation names stable and model the representation type the client actually consumes rather than returning domain entities directly.

## Dependency baseline

Before recommending a new version, check each selected Maven artifact on Maven Central for its latest stable release compatible with the project's Boot and Framework lines.
Keep project BOM or version-catalog management and existing pins unless the task authorizes changing them.

Use the Boot starter for ordinary HATEOAS work.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-hateoas</artifactId>
    </dependency>
</dependencies>
```

The Boot starter is the ordinary activation path.
Open the entity-links reference only when the application needs explicit `@EnableHypermediaSupport`, `EntityLinks`, or aggregate-type-based link derivation.

### Feature-to-artifact map

| Need | Artifact |
| --- | --- |
| HAL item, collection, and paged responses | `spring-boot-starter-hateoas` |
| HAL-FORMS and affordances | `spring-boot-starter-hateoas` |
| EntityLinks and explicit hypermedia activation | `spring-boot-starter-hateoas` |
| `PagedResourcesAssembler` | `spring-data-commons` |

## Initial configuration

For focused test commands and HAL response shapes, read [initial configuration](references/representation-shapes.md#initial-configuration).
Default to HAL unless the project already committed to another hypermedia format.

## Coding procedure

1. Model the representation type explicitly and avoid returning JPA entities or domain aggregates directly.
2. Use `RepresentationModelAssembler` for item and collection representations so link logic is centralized.
3. Build links from controller methods rather than hand-written paths wherever practical.
4. Add affordances only when clients or generated UI flows consume them.
5. Keep link relation names stable once clients depend on them.
6. Test changed representation contracts for the expected link set, including affected item and collection responses.

## Link building

### Controller-method links

For controller-method links, see [example 1](references/configuration-examples.md#example-1-controller-method-links).

### Direct link creation

```java
Link link = Link.of("/orders/{id}", IanaLinkRelations.SELF).expand(order.id());
```

### Templated links

```java
Link templated = Link.of("/orders{?page,size}", "search");
```

`Link.of(...)` recognizes the URI template, so clients can fill in the query parameters.

### Link relations

Use `IanaLinkRelations` constants for standard relations and `LinkRelation.of("...")` for custom ones.

For link relations, see [example 2](references/configuration-examples.md#example-2-link-relations).

### Links concatenation

```java
Links combined = assembler.toModel(order).getLinks()
    .and(Link.of("/docs/orders", IanaLinkRelations.PROFILE));
```

`RepresentationModel.getLinks()` returns a `Links` instance (not `List<Link>`) with concatenation and merge operations.

## Processor boundary

Use an assembler for resource-specific links and use a `RepresentationModelProcessor` only when one cross-cutting rule must enrich many representations the same way.

Keep aggregate-type-based link derivation separate from ordinary assembler work.
Open the entity-links reference only when controller-method links are no longer the right source of truth.

## Implementation examples

For assemblers, controllers, pages, affordances, embedding, WebFlux links, or hypermedia tests, read [implementation examples](references/implementation-examples.md).

For entity or collection response examples and relation names, read [representation shapes](references/representation-shapes.md).

## Testing checklist

Read the affected representation as its client would, including permitted link relations.

- Verify item responses include the required `self` link and any collection or action links the client expects.
- Verify collection and paged responses include navigation links and page metadata where applicable.
- Verify link relations remain stable across refactors.
- Verify affordances appear only on representations that should expose those actions.
- Verify the selected media type is the one actually produced by the endpoint.

## Failure classification

- Treat missing `_links`, broken relation names, or malformed representation shapes as contract failures.
- Treat wrong host, scheme, or base path in generated links as deployment or proxy-configuration failures.
- Treat absent affordances or HAL-FORMS templates as capability-shape failures when clients depend on them.

## Production checklist

- Keep link relation names and URI templates stable after clients are published.
- Avoid exposing links for actions the current user cannot actually perform unless the API contract explicitly allows it.
- Keep one canonical hypermedia media type per module to reduce client ambiguity.
- Ensure reverse proxy or base-path configuration does not break generated links.
- Treat assembler tests as part of the API compatibility surface.
