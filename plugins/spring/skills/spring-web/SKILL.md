---
metadata:
  reference:
    Spring Framework:
      version: 7.0.9
      url:
        - https://docs.spring.io/spring-framework/reference/index.html
        - https://repo.maven.apache.org/maven2/org/springframework/spring-framework-bom/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/spring-framework-bom/7.0.9/spring-framework-bom-7.0.9.pom
name: spring-web
description: >-
  Use Spring MVC/WebFlux for HTTP endpoints, clients, codecs, errors, API versions, and web tests.
---

# Spring Web

## Boundaries

Use this skill for Spring Framework HTTP server and client work: servlet MVC, reactive WebFlux, annotated controllers, request validation, centralized error responses, API versioning, HTTP interface clients, `RestClient`, `WebClient`, `MockMvc`, and `WebTestClient`.
Container lifecycle, transactions, application events, scheduling, and general TestContext wiring are framework-core concerns.
Authentication, authorization, filter chains, CSRF, and bearer-token enforcement are security concerns.

The examples target Spring Framework 7.
This line requires JDK 17+, Jakarta EE 11 for servlet applications, and Netty 4.2 when the reactive runtime uses Reactor Netty.

## Stack selection

Choose servlet MVC for blocking request handling or WebFlux for an end-to-end non-blocking server stack.
Use the server, client, codec, or testing section that matches the task without adding unrelated HTTP infrastructure.

Do not combine MVC and WebFlux in one application merely to use `WebClient`.
A servlet application can use `WebClient` as an outbound client without adopting a reactive server stack.

## Dependency baseline

Use one Spring Framework BOM and keep child modules versionless.
The `7.0.9` BOM below illustrates the documented Framework baseline.
For a new BOM, check `org.springframework:spring-framework-bom` on Maven Central for the latest stable release compatible with the project's Boot line.
Keep an existing BOM or version-catalog pin unless the task authorizes changing it.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-framework-bom</artifactId>
            <version>7.0.9</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Servlet MVC:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Reactive WebFlux:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-webflux</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## First safe commands

```sh
./mvnw test -Dtest=OrderControllerTests,ItemControllerTests
```

```sh
./gradlew test --tests OrderControllerTests --tests ItemControllerTests
```

## Servlet MVC baseline

Enable the MVC infrastructure in a plain Spring application:

```java
@Configuration
@EnableWebMvc
class WebConfig implements WebMvcConfigurer {
}
```

Define a thin controller with validated input:

For Servlet MVC baseline, see [example 1](references/configuration-examples.md#example-1-servlet-mvc-baseline).

Map errors once at the web boundary:

For Servlet MVC baseline, see [example 2](references/configuration-examples.md#example-2-servlet-mvc-baseline).

Use `PathPattern` semantics for HTTP request mappings on Spring Framework 7.
Do not start new HTTP mappings with `AntPathMatcher` assumptions.

## Reactive WebFlux baseline

Choose WebFlux only when controllers, downstream clients, persistence, and other blocking boundaries are designed for non-blocking execution.

For reactive WebFlux baseline, see [example 3](references/configuration-examples.md#example-3-reactive-webflux-baseline).

Keep reactive chains non-blocking and short.
Map domain failures with reactive operators or a centralized advice component rather than calling `block()` in the request path.

```java
@ExceptionHandler(ItemNotFoundException.class)
Mono<ResponseEntity<ErrorResponse>> handleNotFound(ItemNotFoundException ex) {
    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage())));
}
```

## API versioning and HTTP interfaces

Spring Framework 7 supports API-version resolution for MVC and WebFlux through path, header, query-parameter, or media-type strategies.
Keep one strategy consistent across server mappings and clients.

```java
@RestController
@RequestMapping("/orders")
class VersionedOrderController {
    @GetMapping(path = "/{id}", version = "2")
    Order get(@PathVariable Long id) {
        return orders.findById(id);
    }
}
```

Register related HTTP interface clients as a group:

For API versioning and HTTP interfaces, see [example 4](references/configuration-examples.md#example-4-api-versioning-and-http-interfaces).

## Message converters and codecs

Spring Framework 7 uses Jackson 3 for JSON integration.
Register MVC converters only when defaults are not enough:

```java
/**
 * Appends the Jackson 3 converter built from the custom object mapper.
 */
@Override
public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
    converters.add(new JacksonJsonHttpMessageConverter(customMapper));
}
```

For WebFlux, customize `ServerCodecConfigurer` or the client `ExchangeStrategies` only when media types or buffer limits require it.

## RestClient baseline

Use `RestClient` for new imperative HTTP client code.

```java
@Bean
RestClient orderRestClient() {
    return RestClient.builder().baseUrl("https://api.example.com").defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
}

Order loadOrder(RestClient client, long orderId) {
    return client.get().uri("/orders/{id}", orderId).retrieve().body(Order.class);
}
```

## WebClient baseline

Register one configured client and inject it rather than rebuilding it at call sites.

For WebClient baseline, see [example 5](references/configuration-examples.md#example-5-webclient-baseline).

Open [references/webclient-reactive-depth.md](references/webclient-reactive-depth.md) when working with client filters, Reactor Netty timeouts, retry selection, or deeper reactive client behavior.

## Focused web tests

Servlet MVC:

For focused web tests, see [example 6](references/configuration-examples.md#example-6-focused-web-tests).

Reactive WebFlux:

For focused web tests, see [example 7](references/configuration-examples.md#example-7-focused-web-tests).

Use the selected stack to verify the response status, headers, or body that the changed endpoint promises.
Test service-level business behavior without the web infrastructure.

## Production checklist

- Keep controllers thin and centralize exception mapping.
- Keep blocking work out of WebFlux request paths.
- Centralize client base URLs, headers, codecs, and error policy.
- Set client timeouts at the actual transport boundary.
- Treat API versions, media types, error bodies, and HTTP headers as compatibility surfaces.
- Use `HttpHeaders` methods directly.
  It no longer extends `MultiValueMap` in Spring Framework 7.
- Verify proxy and forwarded-header behavior before generating external links or redirects.

## References

- Open [references/webclient-reactive-depth.md](references/webclient-reactive-depth.md) when working with client filters, Reactor Netty timeout configuration, retry placement, or advanced reactive response handling.
