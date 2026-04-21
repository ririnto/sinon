# Spring Data REST exposure

Open this reference when the ordinary repository-abstraction path in [SKILL.md](../SKILL.md) is not enough and the task is specifically about exposing repositories as HTTP resources.

Use Spring Data REST only when repository exposure is an intentional API choice.

This path is for Spring MVC / servlet-based repository exposure. For reactive HTTP endpoints, keep the repository internal and build the contract explicitly in the reactive web layer.

This reference assumes the current stable Spring Data REST line shipped with release train `2025.1.5`. Treat `2026.0.x` material as upcoming until that train reaches GA.

Do not expose repositories directly just because the feature exists.

## Enablement baseline

Add the Spring Data REST web module only when repository exposure is an intentional part of the HTTP contract.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-rest</artifactId>
</dependency>
```

For non-Boot setups, add the Spring Data REST MVC module explicitly.

```xml
<dependency>
    <groupId>org.springframework.data</groupId>
    <artifactId>spring-data-rest-webmvc</artifactId>
</dependency>
```

## Exposure strategy shape

```java
@Configuration
class RestExposureConfiguration implements RepositoryRestConfigurer {
    @Override
    public void configureRepositoryRestConfiguration(RepositoryRestConfiguration config, CorsRegistry cors) {
        config.setRepositoryDetectionStrategy(RepositoryDetectionStrategy.RepositoryDetectionStrategies.ANNOTATED);
    }
}
```

Use `ANNOTATED` when repository exposure must stay explicitly opt-in.

## Repository exposure shape

```java
@RepositoryRestResource(path = "people", rel = "people")
interface PersonRepository extends ListCrudRepository<Person, Long>, PagingAndSortingRepository<Person, Long> {
}
```

This exposes collection and item resources rooted at `/people`.

## HAL response expectation

Spring Data REST serves hypermedia responses by default.

```text
Content-Type: application/hal+json
```

Response shape includes `_links` and paging metadata for pageable collections.

## Paging and sorting shape

```text
GET /people?page=0&size=20&sort=lastname,asc
```

Use pageable exposure only when clients actually need paged repository traversal.

## Projection shape

```java
@Projection(name = "withEmail", types = Person.class)
interface PersonWithEmail {
    String getFirstname();
    String getLastname();
    String getEmail();
}
```

Requested with:

```text
GET /people/1?projection=withEmail
```

Projection interfaces must live in the entity package or a subpackage, or they must be registered manually so Spring Data REST can discover them.

## Decision points

| Situation | Use |
| --- | --- |
| repository is intentionally the public API surface | Spring Data REST can fit |
| HTTP contract needs service-level behavior or custom workflow | explicit controller layer is safer |
| clients depend on links and paging metadata | HAL exposure is useful |

## Default exposure rule

If the application keeps the default repository detection strategy, repositories may be exported without an explicit `@RepositoryRestResource`. Switch to an annotated-only strategy when accidental exposure is unacceptable.

## Verification rule

Verify one HTTP test proves the repository resource path, HAL `_links`, and projection parameter behavior before exposing the repository publicly.
