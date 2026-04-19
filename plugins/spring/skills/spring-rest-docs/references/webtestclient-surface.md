# Spring REST Docs WebTestClient surface

Open this reference when the ordinary MockMvc path is not enough and the task needs WebTestClient.

```java
this.webTestClient = WebTestClient.bindToApplicationContext(context)
    .configureClient()
    .filter(documentationConfiguration(restDocumentation))
    .build();
```

Use this when the application already tests reactive endpoints with `WebTestClient`.

## Validation rule

Verify the generated snippets come from the same reactive exchange path the project already tests.
