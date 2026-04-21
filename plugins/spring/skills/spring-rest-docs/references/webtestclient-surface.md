# Spring REST Docs WebTestClient surface

Open this reference when the ordinary MockMvc path is not enough and the task needs WebTestClient.

## Decision points

| Situation | Use |
| --- | --- |
| Servlet MVC tests already exist | stay in `SKILL.md` with MockMvc |
| Reactive handlers are already tested with WebTestClient | WebTestClient |
| The only reason to switch is stylistic preference | stay on the existing surface |

## Configuration shape

```java
this.webTestClient = WebTestClient.bindToApplicationContext(context)
    .configureClient()
    .filter(documentationConfiguration(restDocumentation))
    .build();
```

Use this when the application already tests reactive endpoints with `WebTestClient`.

## Documentation invocation shape

```java
this.webTestClient.get().uri("/orders/{id}", 42)
    .exchange()
    .expectStatus().isOk()
    .expectBody()
    .consumeWith(document("orders-get",
        pathParameters(
            parameterWithName("id").description("Order identifier")
        ),
        responseFields(
            fieldWithPath("id").description("Order id"),
            fieldWithPath("status").description("Order status")
        )));
```

Attach `document(...)` to the WebTestClient response consumption path rather than trying to reuse the MockMvc `andDo(...)` shape.

## Gotchas

- Do not move a servlet-only module to WebTestClient just to make the docs look uniform.
- Do not document one endpoint with MockMvc and another with WebTestClient in the same module unless the runtime split is real.

## Validation rule

Verify the generated snippets come from the same reactive exchange path the project already tests.
