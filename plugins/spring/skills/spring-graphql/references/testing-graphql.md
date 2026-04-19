# Spring GraphQL testing choices

Open this reference when the blocker is choosing between GraphQL test slices, integration testers, or subscription test setup.

## Slice test blocker

Use `@GraphQlTest` when the test only needs controller mappings, GraphQL wiring, and focused response assertions.

```java
@GraphQlTest(BookGraphQlController.class)
class BookGraphQlControllerTests {
}
```

Mock service dependencies rather than pulling in the whole application when the resolver contract is the thing under test.

## Integration tester blocker

Use `WebGraphQlTester` when the interceptor chain, transport-facing setup, or server integration itself is the point of the test.

## Subscription test blocker

Use reactive assertions when the test must verify a subscription stream instead of a single response.

```java
graphQlTester.document("subscription { bookAdded { id } }")
    .executeSubscription();
```

Keep subscription tests focused on stream shape and completion semantics rather than ordinary query assertions.

## Decision points

| Situation | First check |
| --- | --- |
| Only controller mappings and GraphQL responses matter | use `@GraphQlTest` |
| Interceptors or server wiring matter | use `WebGraphQlTester` |
| The operation is a subscription | use subscription execution with reactive assertions |
| A full application context is not needed | stay on the slice test path |
