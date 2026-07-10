---
name: spring-web
description: >-
  Build servlet MVC and reactive WebFlux HTTP applications with Spring Framework, annotated controllers, centralized errors, API versioning, RestClient, WebClient, and focused web tests.
  Use when choosing MVC versus WebFlux, implementing REST endpoints, registering HTTP interface clients, configuring message codecs, or testing HTTP semantics without relying on Boot autoconfiguration.
---

# Spring Web

## Boundaries

Use this skill for Spring Framework HTTP server and client work: servlet MVC, reactive WebFlux, annotated controllers, request validation, centralized error responses, API versioning, HTTP interface clients, `RestClient`, `WebClient`, `MockMvc`, and `WebTestClient`.
Container lifecycle, transactions, application events, scheduling, and general TestContext wiring are framework-core concerns.
Authentication, authorization, filter chains, CSRF, and bearer-token enforcement are security concerns.

The examples target Spring Framework 7.
This line requires JDK 17+, Jakarta EE 11 for servlet applications, and Netty 4.2 when the reactive runtime uses Reactor Netty.

## Common path

1. Choose servlet MVC for blocking request handling or WebFlux for an end-to-end non-blocking stack.
2. Add only `spring-webmvc` or `spring-webflux` plus the shared test module.
3. Define thin controllers that delegate to application services.
4. Centralize validation failures and exception-to-response mapping.
5. Register one shared HTTP client with its base URL, headers, codecs, and error policy.
6. Add a narrow `MockMvc` or `WebTestClient` test that proves status, headers, and body shape.

Do not combine MVC and WebFlux in one application merely to use `WebClient`; a servlet application can use `WebClient` as an outbound client without adopting a reactive server stack.

## Dependency baseline

Pin the current Spring Framework BOM once and keep child modules versionless.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-framework-bom</artifactId>
            <version>7.0.8</version>
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

```java
@RestController
@RequestMapping("/orders")
class OrderController {
    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/{id}")
    Order get(@PathVariable Long id) {
        return orders.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Order create(@RequestBody @Valid CreateOrderRequest request) {
        return orders.create(request);
    }
}
```

Map errors once at the web boundary:

```java
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse handleNotFound(OrderNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        return new ErrorResponse(ex.getBindingResult().getFieldError().getDefaultMessage());
    }
}
```

Use `PathPattern` semantics for HTTP request mappings on Spring Framework 7.
Do not start new HTTP mappings with `AntPathMatcher` assumptions.

## Reactive WebFlux baseline

Choose WebFlux only when controllers, downstream clients, persistence, and other blocking boundaries are designed for non-blocking execution.

```java
@RestController
@RequestMapping("/items")
class ItemController {
    private final ItemService items;

    ItemController(ItemService items) {
        this.items = items;
    }

    @GetMapping("/{id}")
    Mono<Item> get(@PathVariable Long id) {
        return items.findById(id);
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<Item> stream() {
        return items.streamAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Mono<Item> create(@RequestBody @Valid Mono<CreateItemRequest> request) {
        return request.flatMap(items::create);
    }
}
```

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

```java
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "order", types = {OrderClient.class, OrderAdminClient.class})
class HttpServicesConfiguration {
    @Bean
    RestClientHttpServiceGroupConfigurer groupConfigurer() {
        return groups -> groups.forEachClient((group, builder) -> builder.defaultHeader("User-Agent", "My-App"));
    }
}
```

## Message converters and codecs

Spring Framework 7 uses Jackson 3 for JSON integration.
Register MVC converters only when defaults are not enough:

```java
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

```java
@Bean
WebClient orderWebClient() {
    return WebClient.builder().baseUrl("https://api.example.com").defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
}

Mono<Order> loadOrder(WebClient client, long orderId) {
    return client.get()
        .uri("/orders/{id}", orderId)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, response -> response.bodyToMono(String.class).map(ApiException::new))
        .onStatus(HttpStatusCode::is5xxServerError, response -> response.bodyToMono(String.class).map(UpstreamServiceException::new))
        .bodyToMono(Order.class);
}
```

Open [references/webclient-reactive-depth.md](references/webclient-reactive-depth.md) when filters, Reactor Netty timeouts, retry selection, or deeper reactive client behavior is the blocker.

## Focused web tests

Servlet MVC:

```java
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {AppConfig.class, WebConfig.class})
class OrderControllerTests {
    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void getOrderReturnsOk() throws Exception {
        mockMvc.perform(get("/orders/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }
}
```

Reactive WebFlux:

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AppConfig.class, ReactiveWebConfig.class})
class ItemControllerTests {
    @Autowired
    ApplicationContext context;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @Test
    void getItemReturnsOk() {
        client.get().uri("/items/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody(Item.class)
            .value(item -> assertNotNull(item.id()));
    }
}
```

Keep web tests focused on HTTP semantics.
Test service-level business behavior without the web infrastructure.

## Output contract

Return:

1. The selected MVC or WebFlux server model and required modules
2. The controller, validation, and error-response contract
3. The RestClient, WebClient, or HTTP-interface client configuration
4. The focused HTTP test and any codec, versioning, or transport blocker

## Production checklist

- Keep controllers thin and centralize exception mapping.
- Keep blocking work out of WebFlux request paths.
- Centralize client base URLs, headers, codecs, and error policy.
- Set client timeouts at the actual transport boundary.
- Treat API versions, media types, error bodies, and HTTP headers as compatibility surfaces.
- Use `HttpHeaders` methods directly; it no longer extends `MultiValueMap` in Spring Framework 7.
- Verify proxy and forwarded-header behavior before generating external links or redirects.

## References

- Open [references/webclient-reactive-depth.md](references/webclient-reactive-depth.md) when the blocker is client filters, Reactor Netty timeout configuration, retry placement, or advanced reactive response handling.
