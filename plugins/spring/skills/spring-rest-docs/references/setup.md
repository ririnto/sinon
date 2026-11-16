# Test and build setup

## First safe configuration

## Boot test configuration shape

```java
@WebMvcTest(OrderController.class)
@AutoConfigureRestDocs
class OrderDocumentationTests {
}
```

`@AutoConfigureRestDocs` wires the REST Docs infrastructure into the Boot test slice.
When the output directory or URI rewriting must be customized, keep that customization explicit in the test or in `spring.restdocs.*` configuration.

## Non-Boot test configuration shape

For non-boot test configuration shape, see [example 2](configuration-examples.md#example-2-non-boot-test-configuration-shape).

Use `@ExtendWith(RestDocumentationExtension.class)` when the project does not use Boot test slices.
`RestDocumentationExtension` provides the output directory automatically based on the build tool.

## REST Docs property shape

```yaml
spring:
  restdocs:
    uri:
      scheme: https
      host: api.example.com
      port: 443
```

Keep published URI rewriting stable across local runs and CI jobs.

## First safe commands

```sh
./mvnw package
```

```sh
./gradlew test asciidoctor
```

## Test output directory shape

```text
build/generated-snippets/
```

```text
target/generated-snippets/
```

## Asciidoctor snippets attribute

```adoc
:snippets: build/generated-snippets
```

```adoc
:snippets: target/generated-snippets
```

Keep the snippets directory stable so documentation includes do not drift across modules or CI jobs.

## Gradle publishing shape

For gradle publishing shape, see [example 3](configuration-examples.md#example-3-gradle-publishing-shape).

## Maven publishing shape

For maven publishing shape, see [example 4](configuration-examples.md#example-4-maven-publishing-shape).
