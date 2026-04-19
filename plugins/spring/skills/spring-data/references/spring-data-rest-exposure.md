# Spring Data REST exposure

Open this reference when the ordinary repository-abstraction path in `SKILL.md` is not enough and the task is specifically about exposing repositories as HTTP resources.

Use Spring Data REST only when repository exposure is an intentional API choice.

Do not expose repositories directly just because the feature exists.

## Repository exposure shape

```java
@RepositoryRestResource(path = "people", rel = "people")
interface PersonRepository extends CrudRepository<Person, Long> {
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

## Decision points

| Situation | Use |
| --- | --- |
| repository is intentionally the public API surface | Spring Data REST can fit |
| HTTP contract needs service-level behavior or custom workflow | explicit controller layer is safer |
| clients depend on links and paging metadata | HAL exposure is useful |
