# Graphql implementation examples

## Schema and controller

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
    viewerCanEdit: Boolean!
    reviews: [Review!]!
}

type Review {
    id: ID!
    body: String!
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

## Nested field batching

```java
@BatchMapping(typeName = "Book", field = "reviews")
Map<Book, List<Review>> reviews(List<Book> books) {
    return reviewService.findByBooks(books);
}
```

## Explicit DataLoader variant

```java
@SchemaMapping(typeName = "Book", field = "reviews")
CompletableFuture<List<Review>> reviews(Book book, DataLoader<Long, List<Review>> reviewsLoader) {
    return reviewsLoader.load(book.id());
}
```

Use `@BatchMapping` for the ordinary nested-field batching path.
Keep direct `DataLoader` access for advanced loader registration or reuse across several resolvers.

## Error assertion baseline

```java
graphQlTester.document("mutation { addBook(input: { title: null, author: \"A\" }) { id } }")
    .execute()
    .errors()
    .satisfy(errors -> assertFalse(errors.isEmpty()));
```

## Context propagation baseline

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

Spring GraphQL provides a built-in `HttpRequestHeaderInterceptor` for copying HTTP request headers into the GraphQL context without a custom interceptor.

```java
@Bean
HttpRequestHeaderInterceptor headerInterceptor() {
    return HttpRequestHeaderInterceptor.builder().mapHeader("X-Role").build();
}
```

Keep context values explicit and small.
Read them at resolver boundaries only when the field behavior actually depends on request metadata.

## GraphQL test

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
