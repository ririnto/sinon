---
name: "spring-rest-docs"
description: "Generate Spring REST API documentation from MockMvc or WebTestClient tests and publish snippets via Asciidoctor. Use this skill when generating REST API documentation from Spring tests with MockMvc or WebTestClient snippets, and publishing those snippets through Asciidoctor."
metadata:
  title: "Spring REST Docs"
  official_project_url: "https://spring.io/projects/spring-restdocs"
  reference_doc_urls:
    - "https://docs.spring.io/spring-restdocs/"
    - "https://docs.spring.io/spring-restdocs/tutorial/getting-started/index.html"
  version: "4.0.0"
---

Use this skill when generating REST API documentation from Spring tests with MockMvc or WebTestClient snippets, and publishing those snippets through Asciidoctor.

The latest released Spring REST Docs line is 4.0.0. On this line the ordinary supported documentation surfaces are MockMvc and WebTestClient, so older REST Assured guidance must stay out of the common path.

## Boundaries

Use `spring-rest-docs` for test-driven request and response documentation, generated snippets, preprocessors, field and parameter descriptions, and publishing generated docs.

- Keep hypermedia representation design and link-model construction outside this skill's scope.
- Keep this skill focused on proving and publishing the API contract from tests, not on controller design in general.

## Common path

The ordinary Spring REST Docs job is:

1. Pick the test surface already used by the project: MockMvc for servlet tests or WebTestClient for reactive tests.
2. Add the matching REST Docs test dependency and wire snippet generation into tests.
3. Document the exact request and response fields, parameters, headers, and status codes the endpoint returns.
4. Publish snippets through Asciidoctor includes.
5. Keep documentation generation in CI so snippets fail when the endpoint contract changes.

## Surface map

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| MockMvc | the project already tests servlet endpoints with MockMvc | stay in `SKILL.md` |
| WebTestClient | the endpoint is reactive or the project already uses WebTestClient | open [references/webtestclient-surface.md](references/webtestclient-surface.md) |
| Payload descriptors | field descriptors are the real blocker | open the payload-specific references below |
| Hypermedia links | links are part of the contract | open [references/links.md](references/links.md) |
| Multipart requests | multipart parts are part of the contract | open [references/multipart.md](references/multipart.md) |

## Dependency baseline

Use only the test module that matches the chosen test surface.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.restdocs</groupId>
        <artifactId>spring-restdocs-mockmvc</artifactId>
        <version>4.0.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Feature-to-artifact map

| Need | Artifact |
| --- | --- |
| Servlet tests with MockMvc | `spring-restdocs-mockmvc` |
| Reactive tests with WebTestClient | `spring-restdocs-webtestclient` |
| Asciidoctor integration | `spring-restdocs-asciidoctor` |

Switch the artifact to `spring-restdocs-webtestclient` when the project does not use MockMvc.

## First safe configuration

### Boot test configuration shape

```java
@WebMvcTest(OrderController.class)
@AutoConfigureRestDocs
class OrderDocumentationTests {
}
```

`@AutoConfigureRestDocs` wires the REST Docs infrastructure into the Boot test slice. When the output directory or URI rewriting must be customized, keep that customization explicit in the test or in `spring.restdocs.*` configuration.

### REST Docs property shape

```yaml
spring:
  restdocs:
    uri:
      scheme: https
      host: api.example.com
      port: 443
```

Keep published URI rewriting stable across local runs and CI jobs.

### First safe commands

```bash
./mvnw package
```

```bash
./gradlew test asciidoctor
```

### Test output directory shape

```text
build/generated-snippets/
```

```text
target/generated-snippets/
```

### Asciidoctor snippets attribute

```adoc
:snippets: build/generated-snippets
```

```adoc
:snippets: target/generated-snippets
```

Keep the snippets directory stable so documentation includes do not drift across modules or CI jobs.

### Gradle publishing shape

```kotlin
configurations {
    create("asciidoctorExt")
}

dependencies {
    "asciidoctorExt"("org.springframework.restdocs:spring-restdocs-asciidoctor")
}

val snippetsDir = file("build/generated-snippets")

tasks.test {
    outputs.dir(snippetsDir)
}

tasks.asciidoctor {
    configurations("asciidoctorExt")
    inputs.dir(snippetsDir)
    dependsOn(tasks.test)
}
```

### Maven publishing shape

```xml
    <plugin>
        <groupId>org.asciidoctor</groupId>
        <artifactId>asciidoctor-maven-plugin</artifactId>
        <dependencies>
            <dependency>
                <groupId>org.springframework.restdocs</groupId>
                <artifactId>spring-restdocs-asciidoctor</artifactId>
                <version>4.0.0</version>
            </dependency>
        </dependencies>
        <executions>
            <execution>
                <phase>prepare-package</phase>
                <goals>
                    <goal>process-asciidoc</goal>
                </goals>
        </execution>
    </executions>
    <configuration>
        <attributes>
            <snippets>${project.build.directory}/generated-snippets</snippets>
        </attributes>
    </configuration>
</plugin>
```

## Coding procedure

1. Start from one representative endpoint and make the generated snippets pass before documenting the rest of the API.
2. Keep snippet names stable and endpoint-oriented, such as `orders-create` or `orders-get`.
3. Document every contract element the client depends on: fields, parameters, headers, cookies, links, and multipart parts where relevant.
4. Use preprocessors to pretty-print payloads and mask volatile data before writing snippets.
5. Keep the documentation include file thin and let the test own the truth of the contract.
6. Fail the build when snippets are stale or missing.

## Implementation examples

### MockMvc documentation test

```java
@WebMvcTest(OrderController.class)
@AutoConfigureRestDocs
class OrderDocumentationTests {
    @Autowired
    MockMvc mvc;

    @Test
    void createOrder() throws Exception {
        MockHttpServletRequestBuilder createOrderRequest = post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"ABC\",\"quantity\":2}");
        mvc.perform(createOrderRequest)
            .andExpect(status().isCreated())
            .andDo(document("orders-create", preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()), requestFields(fieldWithPath("sku").description("Stock keeping unit"), fieldWithPath("quantity").description("Quantity requested")), responseFields(fieldWithPath("id").description("Created order id"), fieldWithPath("status").description("Order status"))));
    }
}
```

### Query parameter and header documentation

```java
.andDo(document("orders-list", queryParameters(parameterWithName("status").description("Order status filter")), requestHeaders(headerWithName("X-Correlation-Id").description("Request correlation id")), responseFields(fieldWithPath("[].id").description("Order id"), fieldWithPath("[].status").description("Order status"))));
```

### Path parameter documentation

```java
.andDo(document("orders-get", pathParameters(parameterWithName("orderId").description("Order identifier")), responseFields(fieldWithPath("id").description("Order id"), fieldWithPath("status").description("Order status"))));
```

### URI preprocessing shape

```java
.andDo(document("orders-get", preprocessRequest(modifyUris().scheme("https").host("api.example.com").removePort()), preprocessResponse(prettyPrint())));
```

### Asciidoctor include shape

```adoc
= Orders API
:snippets: build/generated-snippets

== Create order

operation::orders-create[snippets='http-request,http-response,request-fields,response-fields']
```

## Output and configuration shapes

### Stable snippet name shape

```text
orders-create
```

### Generated snippet directory shape

```text
build/generated-snippets/orders-create/
```

### Include contract shape

```adoc
operation::orders-create[snippets='http-request,http-response,response-fields']
```

## Output contract

Return:

1. The chosen test surface and snippet strategy
2. The documented request or response descriptors
3. The publishing path, including the Asciidoctor include shape
4. Any remaining blockers around alternate test surfaces or custom snippets

## Testing checklist

- Verify each documented endpoint test generates the snippets referenced by the published docs.
- Verify request and response field descriptors match the actual payload shape.
- Verify volatile values are normalized or masked before snippets are written.
- Verify the docs build fails when an endpoint contract changes without updating descriptors.
- Verify only one documentation surface per module is treated as canonical output.

## Production checklist

- Keep snippet generation in CI so documentation stays coupled to the tested contract.
- Avoid documenting unstable internal headers or temporary fields as if they were public API.
- Keep snippet names stable to minimize churn in published docs.
- Mask secrets, tokens, and environment-specific hostnames before generating shared snippets.
- Treat generated docs as a release artifact only when the backing tests already pass.

## References

- Open [references/webtestclient-surface.md](references/webtestclient-surface.md) when the ordinary MockMvc path is not enough and the task needs WebTestClient.
- Open [references/relaxed-payloads.md](references/relaxed-payloads.md) when relaxed payload documentation is required.
- Open [references/subsection-payloads.md](references/subsection-payloads.md) when one payload subtree should be documented without every leaf field.
- Open [references/ignored-fields.md](references/ignored-fields.md) when a field must exist in the payload but should stay out of published docs.
- Open [references/query-parameters.md](references/query-parameters.md) when the contract depends on query parameters.
- Open [references/path-parameters.md](references/path-parameters.md) when the contract depends on URI template variables.
- Open [references/cookies.md](references/cookies.md) when the contract depends on cookies.
- Open [references/preprocessors.md](references/preprocessors.md) when preprocessors must do more than the ordinary pretty-print and URI rewriting path.
- Open [references/custom-snippets.md](references/custom-snippets.md) when built-in snippets are not enough.
- Open [references/links.md](references/links.md) when the contract includes hypermedia links.
- Open [references/multipart.md](references/multipart.md) when the contract includes multipart request parts.
