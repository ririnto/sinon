# Spring REST Docs WebTestClient surface

Open this reference when the task needs WebTestClient instead of MockMvc.

## Decision points

| Situation | Use |
| --- | --- |
| Servlet MVC tests already exist | stay in `SKILL.md` with MockMvc |
| Reactive handlers are already tested with WebTestClient | WebTestClient |

## Configuration

```java
@ExtendWith(RestDocumentationExtension.class)
class OrderDocumentationTests {
    WebTestClient webTestClient;

    @BeforeEach
    void setUp(ApplicationContext context, RestDocumentationContextProvider restDocumentation) {
        this.webTestClient = WebTestClient.bindToApplicationContext(context)
            .configureClient()
            .filter(documentationConfiguration(restDocumentation))
            .build();
    }
}
```

Attach `document(...)` to WebTestClient response path rather than trying to reuse MockMvc `andDo(...)`.

```java
this.webTestClient.get().uri("/orders/{id}", 42)
    .exchange()
    .expectStatus().isOk()
    .expectBody()
    .consumeWith(document("orders-get", pathParameters(parameterWithName("id").description("Order identifier")),
        responseFields(fieldWithPath("id").description("Order id"), fieldWithPath("status").description("Order status"))));
```

## Gotchas

- Do not move a servlet-only module to WebTestClient unless it is genuinely reactive.
- Do not document one endpoint with MockMvc and another with WebTestClient in the same module unless the runtime split is real.

## Validation rule

Verify generated snippets come from the same reactive exchange path as the project tests.
