# Spring GraphQL testing choices

Open this reference when the blocker is choosing between GraphQL test slices, integration testers, or subscription test setup.

## Slice test blocker

Use `@GraphQlTest` when the test only needs controller mappings, GraphQL wiring, and focused response assertions.
It scans `@Controller`, `@ControllerAdvice`, `RuntimeWiringConfigurer`, `@JacksonComponent`, `Converter`, `GenericConverter`, `DataFetcherExceptionResolver`, `Instrumentation`, and `GraphQlSourceBuilderCustomizer` beans.
Regular `@Component` and `@ConfigurationProperties` beans are not scanned.
Use `@EnableConfigurationProperties` to include them.

```java
@GraphQlTest(BookGraphQlController.class)
class BookGraphQlControllerTests {
}
```

Mock service dependencies rather than pulling in the whole application when the resolver contract is the thing under test.

## Integration tester blocker

Use `HttpGraphQlTester` when the interceptor chain, transport-facing setup, or server integration itself is the point of the test.

With `@SpringBootTest` on a random or defined port, `HttpGraphQlTester` is auto-configured.
For a MOCK environment, use `@AutoConfigureHttpGraphQlTester`.

```java
@AutoConfigureHttpGraphQlTester
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class GraphQlIntegrationTests {
    @Autowired
    HttpGraphQlTester graphQlTester;
}
```

## Subscription test blocker

Use reactive assertions when the test must verify a subscription stream instead of a single response.

```java
graphQlTester.document("subscription { bookAdded { id } }")
    .executeSubscription();
```

`GraphQlTester.EntityList` in 2.0.3+ supports `singleElement()` for verifying a list response contains exactly one item.

Keep subscription tests focused on stream shape and completion semantics rather than ordinary query assertions.

## Decision points

| Situation | First check |
| --- | --- |
| Only controller mappings and GraphQL responses matter | use `@GraphQlTest` |
| Interceptors or server wiring matter | use `HttpGraphQlTester` |
| The operation is a subscription | use subscription execution with reactive assertions |
| A full application context is not needed | stay on the slice test path |

## Validation rule

Verify the chosen tester exercises the same resolver, interceptor, and transport boundary that the production request path depends on.
