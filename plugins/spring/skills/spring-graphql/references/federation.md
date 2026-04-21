# Spring GraphQL federation

Open this reference when the schema is intentionally part of a federated graph and the ordinary single-service schema path in `SKILL.md` is not enough.

## Federation boundary

Use federation only when the GraphQL API is intentionally part of a federated graph.

Federation adds schema ownership and entity-resolution complexity. Do not introduce it for a single-service schema.

## Entity loading

Model federation entity resolution explicitly so ownership boundaries stay clear between services.

```java
@Controller
class BookEntityController {
    @EntityMapping
    Book book(@Argument int id) {
        return bookService.find(id);
    }
}
```

Use entity mappings to resolve federated references back into the local aggregate the service owns.

## Schema extension shape

```graphql
type Book @key(fields: "id") {
  id: ID!
  title: String!
}
```

## Decision points

| Situation | Use |
| --- | --- |
| One service owns the whole schema | stay on the ordinary schema-first path |
| Multiple services contribute entity fields into one graph | federation |
| No clear graph ownership reason | do not add federation |

## Validation rule

Verify that entity ownership and `@EntityMapping` resolution boundaries are explicit before adding federation-specific schema directives or controllers.
