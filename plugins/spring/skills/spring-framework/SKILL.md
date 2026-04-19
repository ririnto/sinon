---
name: "spring-framework"
description: "Use this skill when the task depends on core Spring Framework APIs rather than Boot conventions, especially the container, Java configuration, bean lifecycle, transactions, events, validation, servlet MVC controllers and exception handling, reactive HTTP with WebFlux, WebClient, and TestContext support."
metadata:
  title: "Spring Framework"
  official_project_url: "https://spring.io/projects/spring-framework"
  reference_doc_urls:
    - "https://docs.spring.io/spring-framework/reference/index.html"
    - "https://docs.spring.io/spring-framework/reference/core.html"
    - "https://docs.spring.io/spring-framework/reference/testing.html"
  version: "7.0.7"
---

Use this skill when the task depends on core Spring Framework APIs rather than Boot conventions, especially the container, Java configuration, bean lifecycle, transactions, events, validation, servlet MVC controllers and exception handling, reactive HTTP with WebFlux, WebClient, and TestContext support.

## Boundaries

Use spring-framework for core Spring Framework APIs: container behavior, bean wiring, lifecycle hooks, transactions, events, scheduling, property binding, conversion, validation, ordinary servlet MVC, reactive HTTP, WebClient, and TestContext-driven framework tests.

Use narrower guidance when the task is primarily about security or Boot auto-configuration rather than Spring Framework modules and APIs.

## Common path

The ordinary Spring Framework job is:

1. Choose the smallest Spring modules that match the needed capability.
2. Define the application wiring with Java configuration and explicit beans.
3. Control the environment, profiles, and externalized configuration explicitly.
4. Keep bean lifecycle, events, transactions, conversion, validation, and scheduling behavior explicit.
5. Add a focused TestContext-based or plain Spring test that proves the framework integration works.

## Module selection

Use only the Spring Framework modules the application actually needs.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-context</artifactId>
        <version>7.0.7</version>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-tx</artifactId>
        <version>7.0.7</version>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-test</artifactId>
        <version>7.0.7</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Add `spring-webmvc`, `spring-webflux`, `spring-jdbc`, or other modules only when the task truly needs them.

## Java configuration

```java
@Configuration
class AppConfig {
    @Bean
    InventoryService inventoryService(InventoryRepository repository) {
        return new InventoryService(repository);
    }
}
```

Prefer constructor injection and explicit bean graphs. Start with explicit `@Bean` wiring before reaching for broader framework indirection.

## Environment, profiles, and resources

### Activating profiles

```java
AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
ctx.getEnvironment().setActiveProfiles("test");
```

### Property source configuration

```java
@Configuration
@PropertySource("classpath:app.properties")
class AppConfig {
}
```

### Accessing environment properties

```java
@Autowired
ApplicationContext ctx;

String value = ctx.getEnvironment().getProperty("db.url");
```

### Resource loading

```java
Resource resource = ctx.getResource("classpath:data.json");
```

Use the environment to externalize configuration and profiles to control which beans or configurations are active. Keep resource loading explicit when the application needs files from the classpath or filesystem.

## Bean scopes

| Scope       | Use when                                      |
| --- | --- |
| `singleton` | one shared instance per container (default)   |
| `prototype` | new instance every time the bean is requested |
| custom scope | lifecycle is neither singleton nor prototype and requires explicit scope registration |

```java
@Bean
@Scope("prototype")
MyPrototypeBean prototypeBean() {
    return new MyPrototypeBean();
}
```

Use singleton by default. Reach for prototype only when the lifecycle difference genuinely matters. Web-specific scopes belong to web-focused configurations rather than the ordinary framework-core path.

Open [references/container-extension-scopes.md](references/container-extension-scopes.md) when the task needs a custom scope, container extension point, or clarification of `@Configuration` lite mode.

## Bean lifecycle

### Initialization and destruction callbacks

```java
@Bean(initMethod = "init", destroyMethod = "cleanup")
MyService myService() {
    return new MyService();
}
```

Or use `@PostConstruct` and `@PreDestroy`:

```java
@Component
class InventoryWarmup {
    @PostConstruct
    void init() {
    }

    @PreDestroy
    void cleanup() {
    }
}
```

### Context refresh event listener

```java
@Component
class InventoryWarmup implements ApplicationListener<ContextRefreshedEvent> {
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
    }
}
```

Use lifecycle hooks only when initialization or shutdown semantics genuinely matter. Prefer one lifecycle style consistently instead of mixing `@PostConstruct` / `@PreDestroy` with `initMethod` / `destroyMethod` in the same component graph.

## Application events

Publish events for decoupled follow-up work:

```java
@Service
class OrderService {
    private final ApplicationEventPublisher events;

    OrderService(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void place(Order order) {
        events.publishEvent(new OrderPlacedEvent(this, order));
    }
}
```

Listen to events:

```java
@Component
class OrderNotificationListener implements ApplicationListener<OrderPlacedEvent> {
    @Override
    public void onApplicationEvent(OrderPlacedEvent event) {
    }
}
```

Use application events for genuinely decoupled follow-up work, not as a substitute for basic method calls. Keep event classes immutable and scoped to the application package.

Open [references/container-extension-scopes.md](references/container-extension-scopes.md) when the task depends on ordered listeners, `@EventListener` conditions, or lower-level listener infrastructure.

## Data binding and conversion

### Binding incoming values onto an object

```java
DataBinder binder = new DataBinder(new InventoryForm());
binder.bind(new MutablePropertyValues(Map.of("maxItems", "100")));
```

### Custom conversion

```java
@Configuration
class AppConfig {
    @Bean
    ConversionService conversionService() {
        DefaultConversionService service = new DefaultConversionService();
        service.addConverter(new MyCustomConverter());
        return service;
    }
}
```

Use `DataBinder` when the framework must bind incoming values onto an object. Add custom converters only when the framework does not provide the needed conversion.

## Validation

```java
@Bean
MethodValidationPostProcessor methodValidationPostProcessor() {
    return new MethodValidationPostProcessor();
}

@Service
@Validated
class TransferService {
    @Transactional
    void transfer(@NotNull Account from, @NotNull Account to, @Positive BigDecimal amount) {
    }
}
```

Validate at the boundary where input enters the application. Use standard Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`) on input objects, and register method-validation infrastructure explicitly when service-layer method validation is part of the ordinary path.

## Servlet MVC

Enable the MVC infrastructure with a configuration class and a `DispatcherServlet` registration, or let a servlet container initializer wire both together. The controller examples below assume the infrastructure is already in place and focus on controller shape.

```java
@Configuration
@EnableWebMvc
class WebConfig implements WebMvcConfigurer {
}
```

Define controllers with constructor-injected dependencies:

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

Handle exceptions centrally with `@RestControllerAdvice`:

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

Keep controllers thin: delegate all logic to services. Use `@RestControllerAdvice` as a single exception boundary rather than scattering `try/catch` blocks across controllers.

## Reactive HTTP

Add `spring-webflux` and use annotated controllers returning `Mono` and `Flux`. The examples below assume WebFlux infrastructure is already configured and focus on controller shape.

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

Handle errors in the reactive chain with `onErrorMap` or a `@RestControllerAdvice` that returns `Mono<ResponseEntity<?>>`:

```java
@ExceptionHandler(ItemNotFoundException.class)
Mono<ResponseEntity<ErrorResponse>> handleNotFound(ItemNotFoundException ex) {
    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse(ex.getMessage())));
}
```

Keep operator chains short. Return early by flatMapping into the service rather than blocking.

## WebClient

In Spring Boot, `WebClient.Builder` is auto-registered and can be injected directly. In plain Spring Framework, register the builder explicitly as shown below.

Build a `WebClient` bean once and inject it where needed:

```java
@Bean
WebClient.Builder webClientBuilder() {
    return WebClient.builder();
}

@Bean
WebClient webClient(WebClient.Builder builder) {
    return builder
        .baseUrl("https://api.example.com")
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
}
```

Make a typed GET request:

```java
Mono<Order> order = client.get()
    .uri("/orders/{id}", orderId)
    .retrieve()
    .bodyToMono(Order.class);
```

Handle 4xx and 5xx responses explicitly rather than letting them propagate as `WebClientResponseException`:

```java
Mono<Order> order = client.get()
    .uri("/orders/{id}", orderId)
    .retrieve()
    .onStatus(HttpStatusCode::is4xxClientError,
        response -> response.bodyToMono(String.class).map(ApiException::new))
    .bodyToMono(Order.class);
```

Use `bodyToFlux` for streaming responses and `ExchangeStrategies` when the default codec buffer limit needs adjustment.

## Transaction boundary

Enable transaction management explicitly in plain Spring Framework:

```java
@Configuration
@EnableTransactionManagement
class TxConfig {
}
```

Pair this with the appropriate `PlatformTransactionManager` bean for the chosen data-access technology.

```java
@Service
class TransferService {
    private final AccountRepository accounts;

    TransferService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    void transfer(String from, String to, BigDecimal amount) {
        accounts.debit(from, amount);
        accounts.credit(to, amount);
    }
}
```

Keep transaction boundaries on service methods that own one business unit of work. Avoid transactions that span multiple unrelated operations.

## Scheduling

```java
@Configuration
@EnableScheduling
class AppConfig {
}
```

Scheduled method:

```java
@Component
class InventoryCleanup {
    @Scheduled(cron = "0 0 2 * * ?")
    void cleanup() {
    }
}
```

Use `@EnableScheduling` to activate framework-scheduled tasks. Keep scheduled jobs idempotent and document the cron expression.

Do not stack `@Async` and `@Scheduled` on the same method casually. Treat that combination as a proxy and executor design decision rather than ordinary scheduling.

## Cache and AOT escalation

Use `@EnableCaching` only when the task explicitly needs framework-managed cache annotations and the cache boundary is clear.

Treat AOT and native-image hints as an escalation point rather than part of the ordinary path. Keep the default implementation reflective and explicit until the task specifically requires AOT-friendly wiring.

## AOP escalation

Open [references/aop-cross-cutting.md](references/aop-cross-cutting.md) when cross-cutting behavior must wrap many beans consistently and the ordinary bean wiring path is not enough.

## Container extension escalation

Open [references/container-extension-scopes.md](references/container-extension-scopes.md) when the task needs to customize bean definitions, post-process beans, register a custom scope, or understand `@Configuration` lite mode versus full configuration.

## TestContext integration

```java
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AppConfig.class)
class AppConfigTests {
    @Autowired
    InventoryService inventoryService;

    @Test
    void contextLoads() {
        assertNotNull(inventoryService);
    }
}
```

Test the smallest framework integration that proves the behavior. Use `@ExtendWith(SpringExtension.class)` to integrate the Spring `ApplicationContext` with JUnit Jupiter.

## MVC and reactive tests

Test a servlet MVC controller with `MockMvc` wired from the `WebApplicationContext`:

```java
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {AppConfig.class, WebConfig.class})
class OrderControllerTests {
    @Autowired
    WebApplicationContext wac;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @Test
    void getOrder_returnsOk() throws Exception {
        mockMvc.perform(get("/orders/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void createOrder_withInvalidBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }
}
```

Test a reactive controller or `WebClient` interaction with `WebTestClient`:

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AppConfig.class, ReactiveWebConfig.class})
class ItemControllerTests {
    @Autowired
    ApplicationContext ctx;

    WebTestClient client;

    @BeforeEach
    void setup() {
        client = WebTestClient.bindToApplicationContext(ctx).build();
    }

    @Test
    void getItem_returnsOk() {
        client.get().uri("/items/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody(Item.class)
            .value(item -> assertNotNull(item.id()));
    }
}
```

Keep controller tests focused on HTTP semantics: status codes, headers, and response shape. Delegate business-logic assertions to plain unit tests against the service layer.

## Output shapes

### Bean declaration

```java
@Bean
InventoryService inventoryService(InventoryRepository repository)
```

### Event listener

```java
class InventoryWarmup implements ApplicationListener<ContextRefreshedEvent>
```

### `@ContextConfiguration`

```java
@ContextConfiguration(classes = AppConfig.class)
```

## Testing checklist

- Verify the application context loads with the intended bean graph.
- Verify transaction boundaries wrap the intended unit of work.
- Verify lifecycle or event callbacks run at the correct framework phase.
- Verify validation or conversion logic applies at the expected boundary.
- Verify MVC controllers return the correct HTTP status and response shape under `MockMvc`.
- Verify reactive controllers and `WebClient` interactions return the expected status and body under `WebTestClient`.
- Verify `@RestControllerAdvice` exception mappings produce the intended status codes.
- Verify framework tests stay narrow enough to isolate the intended integration behavior.

## Production checklist

- Keep bean wiring explicit enough that startup failures remain diagnosable.
- Avoid transaction boundaries broader than one business unit of work.
- Use lifecycle hooks sparingly and document why they are needed.
- Keep application events purposeful rather than turning them into hidden control flow.
- Treat Spring integration tests as part of the framework compatibility surface.

## References

- Open [references/aop-cross-cutting.md](references/aop-cross-cutting.md) when the task needs framework-level AOP beyond ordinary bean wiring.
- Open [references/container-extension-scopes.md](references/container-extension-scopes.md) when the blocker is container extension points, custom scopes, advanced listener infrastructure, or `@Configuration` lite-mode behavior.
- Open [references/environment-and-resources.md](references/environment-and-resources.md) when the task needs deeper control over profiles, property sources, or resource resolution beyond the common path.
- Open [references/property-binding-conversion-validation.md](references/property-binding-conversion-validation.md) when the task needs advanced data-binding rules, formatter/converter registration, or validation groups beyond the common path.
