---
metadata:
  reference:
    Spring REST Docs:
      version: 4.0.1
      url:
        - https://docs.spring.io/spring-restdocs/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/restdocs/spring-restdocs-bom/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/restdocs/spring-restdocs-bom/4.0.1/spring-restdocs-bom-4.0.1.pom
name: spring-rest-docs
description: >-
  Use Spring REST Docs to generate and publish API documentation from MockMvc/WebTestClient tests.
---

# Spring REST Docs

Spring REST Docs 4.0.1 requires Spring Framework 7.0 (Spring Boot 4.x), JUnit 6.0, AsciidoctorJ 3.0, Jackson 3, and Bean Validation 3.1 (Hibernate Validator 9.0).
REST Assured support was removed in 4.0.
The only supported documentation surfaces are MockMvc and WebTestClient.

## Boundaries

Use `spring-rest-docs` for test-driven request and response documentation, generated snippets, preprocessors, field and parameter descriptions, and publishing generated docs.

- Keep hypermedia representation design and link-model construction outside this skill's scope.
- Keep this skill focused on proving and publishing the API contract from tests, not on controller design in general.

## Task scope

Use the project's existing MockMvc or WebTestClient surface and document the affected endpoint contract.
Generate snippets and build the referenced Asciidoctor output before declaring documentation changes complete.
Preparing documentation does not authorize publishing it to an external site or service.

## Surface map

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| MockMvc | the project already tests servlet endpoints with MockMvc | stay in `SKILL.md` |
| WebTestClient | the endpoint is reactive or the project already uses WebTestClient | open [references/webtestclient-surface.md](references/webtestclient-surface.md) |
| Payload descriptors | field descriptors are the real blocker | open the payload-specific references below |
| Hypermedia links | links are part of the contract | open [references/links.md](references/links.md) |
| HTTP headers | request or response headers are part of the contract | open [references/http-headers.md](references/http-headers.md) |
| Cookies | cookies are part of the contract | open [references/cookies.md](references/cookies.md) |
| Multipart requests | multipart parts are part of the contract | open [references/multipart.md](references/multipart.md) |

## Dependency baseline

Use only the test module that matches the chosen test surface.
The `4.0.1` versions below illustrate the documented Spring REST Docs baseline.
For new dependencies, check each `org.springframework.restdocs` artifact on Maven Central for the latest stable release compatible with the project's Boot and Framework lines.
Keep an existing BOM or version-catalog pin unless the task authorizes changing it.

For dependency baseline, see [example 1](references/configuration-examples.md#example-1-dependency-baseline).

### Feature-to-artifact map

| Need | Artifact |
| --- | --- |
| Servlet tests with MockMvc | `spring-restdocs-mockmvc` |
| Reactive tests with WebTestClient | `spring-restdocs-webtestclient` |
| Asciidoctor integration | `spring-restdocs-asciidoctor` |

Switch the artifact to `spring-restdocs-webtestclient` when the project does not use MockMvc.

## Test and build setup

For Boot or non-Boot test setup, URI rewriting, snippet paths, and Gradle or Maven builds, read [test and build setup](references/setup.md).
Keep URI rewriting and snippet directories stable across modules, local runs, and CI.
Use `@ExtendWith(RestDocumentationExtension.class)` for non-Boot tests.
For sample dependency, test, and build configuration, read [configuration examples](references/configuration-examples.md).

## Coding procedure

1. Update the documentation tests for the endpoints in scope rather than rebuilding unrelated API documentation.
2. Keep snippet names stable and endpoint-oriented, such as `orders-create` or `orders-get`.
3. Document every contract element the client depends on: fields, parameters, headers, cookies, links, and multipart parts where relevant.
4. Use preprocessors to pretty-print payloads and mask volatile data before writing snippets.
5. Keep the documentation include file thin and let the test own the truth of the contract.
6. Fail the build when snippets are stale or missing.

## Implementation examples

- For MockMvc documentation test, see [the example](references/implementation-examples.md#mockmvc-documentation-test).
- For query parameter and header documentation, see [the example](references/implementation-examples.md#query-parameter-and-header-documentation).
- For path parameter documentation, see [the example](references/implementation-examples.md#path-parameter-documentation).
- For URI preprocessing shape, see [the example](references/implementation-examples.md#uri-preprocessing-shape).
- For Asciidoctor include shape, see [the example](references/implementation-examples.md#asciidoctor-include-shape).

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

The surface map routes WebTestClient, links, headers, cookies, and multipart requests.
Read the relevant reference for these additional conditions:

- [Relaxed payloads](references/relaxed-payloads.md): relaxed payload documentation.
- [Subsection payloads](references/subsection-payloads.md): document one subtree without every leaf.
- [Ignored fields](references/ignored-fields.md): require a field without publishing it.
- [Query parameters](references/query-parameters.md): query parameters are part of the contract.
- [Path parameters](references/path-parameters.md): URI template variables are part of the contract.
- [Preprocessors](references/preprocessors.md): preprocessing beyond pretty-printing and URI rewriting.
- [Custom snippets](references/custom-snippets.md): built-in snippets are insufficient.
