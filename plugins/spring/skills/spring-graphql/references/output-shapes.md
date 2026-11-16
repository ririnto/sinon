# Output and configuration shapes

## Query path shape

```text
bookById.title
```

## GraphQL endpoint shape

```text
/graphql
```

## DataLoader field-mapping shape

```java
@SchemaMapping(typeName = "Book", field = "reviews")
```

## Batch mapping shape

```java
@BatchMapping(typeName = "Book", field = "reviews")
```
