---
name: "spring-graphql"
description: "Build Spring GraphQL servers with schema resources, annotated query or mutation mappings, batching, transport-aware execution, and `GraphQlTester`-based tests. Use this skill when building Spring GraphQL servers with schema resources, annotated query or mutation mappings, batching, transport-aware execution, and `GraphQlTester`-based tests."
metadata:
    title: "Spring for GraphQL"
    official_project_url: "https://spring.io/projects/spring-graphql"
    reference_doc_urls:
        - "https://docs.spring.io/spring-graphql/reference/index.html"
    version: "2.0.3"
---

Use this skill when building Spring GraphQL servers with schema resources, annotated query or mutation mappings, batching, transport-aware execution, and `GraphQlTester`-based tests.

## Boundaries

Use `spring-graphql` for GraphQL schema design, query and mutation handlers, field mappings, batching, transport-aware execution, and GraphQL-specific tests.

- Use narrower guidance for ordinary REST or non-GraphQL HTTP endpoint design.
- Keep transport and resolver concerns at the GraphQL boundary. Domain services should not depend on GraphQL-specific annotations or response shapes.
- Keep federation out of the ordinary path. Open the federation reference only when the graph is intentionally split across several services.

## Common path

The ordinary Spring GraphQL job is:

1. Define the GraphQL schema first and keep field names stable.
2. Map root fields with `@QueryMapping` or `@MutationMapping` and nested fields with `@SchemaMapping` or `@BatchMapping` only when needed.
3. Use `@BatchMapping` or DataLoader when the resolver shape would otherwise cause N+1 access patterns.
4. Keep GraphQL error categories intentional so validation, authorization, and execution failures stay distinguishable.
5. Add a `GraphQlTester` test that proves the query shape, response path, and error behavior.
6. Keep one canonical transport, usually HTTP, unless the application truly needs subscriptions or another protocol.

## Surface map

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| Ordinary schema-first query and mutation API | one service owns the schema and HTTP transport is enough | stay in `SKILL.md` |
| Subscription or alternate transports | subscriptions, WebSocket, or RSocket are the blocker | open [references/transports-and-subscriptions.md](references/transports-and-subscriptions.md) |
| Custom batching lifecycle | `@BatchMapping` is not enough | open [references/advanced-dataloader.md](references/advanced-dataloader.md) |
| Test-slice or integration-tester choice | the blocker is choosing the right GraphQL test surface | open [references/testing-graphql.md](references/testing-graphql.md) |
| Security context or explicit error shaping | request metadata, authorization, or stable error categories are the blocker | open [references/security-context-and-errors.md](references/security-context-and-errors.md) |
| Federated graph ownership | more than one service contributes to the graph | open [references/federation.md](references/federation.md) |

## Dependency baseline

Use the Boot starter for application code and the GraphQL test module for focused tests.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-graphql</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.graphql</groupId>
        <artifactId>spring-graphql-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Feature-to-artifact map

| Need | Artifact |
| --- | --- |
| Ordinary GraphQL controllers, schema resources, and HTTP endpoint | `spring-boot-starter-graphql` |
| GraphQL-focused test slices and tester support | `spring-graphql-test` |
| Subscription, WebSocket, or RSocket transport support | `spring-boot-starter-graphql` with transport-specific configuration |

## First safe configuration

### First safe commands

```bash
./mvnw test -Dtest=BookGraphQlControllerTests
```

```bash
./gradlew test --tests BookGraphQlControllerTests
```

### Schema resource shape

```text
src/main/resources/graphql/schema.graphqls
```

### HTTP endpoint shape

```yaml
spring:
    graphql:
        path: /graphql
```

Start with a single schema resource set and one HTTP endpoint. Add WebSocket or RSocket transports only when the API really needs them.

## Build and run path

```bash
./mvnw spring-boot:run
```

```bash
./gradlew bootRun
```

Run the server on the ordinary HTTP path first. Add WebSocket or RSocket verification only after the base schema and resolver path is stable.

## Mapping and batching decisions

| Situation | Use |
| --- | --- |
| Root query or mutation field | `@QueryMapping` or `@MutationMapping` |
| Nested field resolved from one parent object | `@SchemaMapping` |
| Nested field resolved for many parents at once | `@BatchMapping` |
| Custom loader options, several loader keys, or shared batch lifecycle | dedicated DataLoader registration |

Prefer `@BatchMapping` when one nested field is the obvious batching boundary. Keep manual DataLoader registration for cases where batching must be shared across several resolvers or keyed differently from the schema field itself.

## Coding procedure

1. Keep schema field names and nullability intentional because clients depend on them directly.
2. Use annotated controllers for ordinary query, mutation, and field mappings instead of low-level wiring when the codebase already follows Spring conventions.
3. Return domain data mapped to schema-facing DTOs or stable shapes instead of leaking persistence entities casually.
4. Prefer `@BatchMapping` first, then reach for explicit DataLoader registration when batching needs custom loader composition.
5. Keep authorization and context propagation explicit at the GraphQL boundary.
6. Make validation, authorization, and execution failures observable as distinct GraphQL error categories.
7. Test both the happy path and at least one invalid-input or authorization failure path.

## Implementation examples

### Schema and controller

```graphql
type Query {
    bookById(id: ID!): Book
}

type Mutation {
    addBook(input: BookInput!): Book!
}

type Book {
    id: ID!
    title: String!
    author: String!
}

input BookInput {
    title: String!
    author: String!
}
```

```java
@Controller
class BookGraphQlController {
    private final BookService service;

    BookGraphQlController(BookService service) {
        this.service = service;
    }

    @QueryMapping
    Book bookById(@Argument Long id) {
        return service.find(id);
    }

    @MutationMapping
    Book addBook(@Argument BookInput input) {
        return service.add(input);
    }

    @SchemaMapping(typeName = "Book", field = "viewerCanEdit")
    boolean viewerCanEdit(Book book, GraphQLContext context) {
        return context.getOrDefault("role", "viewer").equals("editor");
    }
}
```

### Nested field batching

```java
@BatchMapping(typeName = "Book", field = "reviews")
Map<Book, List<Review>> reviews(List<Book> books) {
    return reviewService.findByBooks(books);
}
```

### Explicit DataLoader variant

```java
@SchemaMapping(typeName = "Book", field = "reviews")
CompletableFuture<List<Review>> reviews(Book book, DataLoader<Long, List<Review>> reviewsLoader) {
    return reviewsLoader.load(book.id());
}
```

Use `@BatchMapping` for the ordinary nested-field batching path. Keep direct `DataLoader` access for advanced loader registration or reuse across several resolvers.

### Error assertion baseline

```java
graphQlTester.document("mutation { addBook(input: { title: \"\", author: \"A\" }) { id } }")
    .execute()
    .errors()
    .satisfy(errors -> assertFalse(errors.isEmpty()));
```

### Context propagation baseline

```java
@Bean
WebGraphQlInterceptor roleInterceptor() {
    return (request, chain) -> {
        String role = request.getHeaders().getFirst("X-Role");
        if (role != null) {
            request.configureExecutionInput((input, builder) -> builder.graphQLContext(Map.of("role", role)).build());
        }
        return chain.next(request);
    };
}
```

Keep context values explicit and small. Read them at resolver boundaries only when the field behavior actually depends on request metadata.

### GraphQL test

```java
@GraphQlTest(BookGraphQlController.class)
class BookGraphQlControllerTests {
    @Autowired
    GraphQlTester graphQlTester;

    @Test
    void query() {
        graphQlTester.document("query { bookById(id: 1) { title } }")
            .execute()
            .path("bookById.title")
            .entity(String.class)
            .isEqualTo("Spring");
    }
}
```

## Testing slice choice

| Situation | Use |
| --- | --- |
| Controller-focused GraphQL mapping test | `@GraphQlTest` + `GraphQlTester` |
| Server integration with interceptor chain | `WebGraphQlTester` |
| Subscription verification | `GraphQlTester` + reactive assertions |

Open the testing reference when transport choice, subscription testing, or slice-versus-integration setup becomes the blocker.

## Output and configuration shapes

### Query path shape

```text
bookById.title
```

### GraphQL endpoint shape

```text
/graphql
```

### DataLoader field-mapping shape

```java
@SchemaMapping(typeName = "Book", field = "reviews")
```

### Batch mapping shape

```java
@BatchMapping(typeName = "Book", field = "reviews")
```

## Testing checklist

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

## Output contract

Return:

1. The chosen schema and controller mapping shape for the operation
2. The batching strategy, including whether `@BatchMapping` or explicit DataLoader registration is used
3. The selected transport path and any transport-specific configuration
4. The GraphQlTester or integration test shape proving the response and error behavior
5. The intended GraphQL error categories and context-propagation approach
6. Any blocker that requires subscriptions, custom DataLoader wiring, security interception, or federation

## References

- Open [references/transports-and-subscriptions.md](references/transports-and-subscriptions.md) when the ordinary HTTP server path is not enough and the task needs subscriptions, WebSocket, or RSocket transport wiring.
- Open [references/advanced-dataloader.md](references/advanced-dataloader.md) when the blocker is DataLoader registration, options, shared batching lifecycle, or batch-loader context.
- Open [references/testing-graphql.md](references/testing-graphql.md) when the blocker is choosing between GraphQL test slices, integration testers, or subscription test setup.
- Open [references/security-context-and-errors.md](references/security-context-and-errors.md) when the task needs interceptor-based context propagation, authorization handling, or more explicit GraphQL error shaping.
- Open [references/federation.md](references/federation.md) when the schema is intentionally part of a federated graph.
