---
name: spring-framework
description: >-
  Apply core Spring Framework 7.x APIs for the container, Java configuration, bean lifecycle, transactions, events, validation, scheduling, async, resilience, servlet MVC, WebFlux, WebClient, and TestContext support.
  Use when configuring `@Configuration` classes, managing bean scopes and lifecycle callbacks, setting up declarative transaction boundaries, enabling `@Retryable` resilience, API versioning, programmatic bean registration, or wiring `WebFlux` and `WebClient` without Boot autoconfiguration.
---

# Spring Framework

## Boundaries

Use spring-framework for core Spring Framework APIs: container behavior, bean wiring, lifecycle hooks, transactions, events, scheduling, property binding, conversion, validation, ordinary servlet MVC, reactive HTTP, WebClient, and TestContext-driven framework tests.

Use narrower guidance when the task is primarily about security or Boot auto-configuration rather than Spring Framework modules and APIs.

## Baseline requirements

Spring Framework 7.0.8 requires:

- JDK 17 minimum; JDK 25 recommended as the latest LTS
- Jakarta EE 11 (Servlet 6.1, JPA 3.2, Bean Validation 3.1)
- JUnit 6 (JUnit 6 is the target test line)
- Kotlin 2.2 for Kotlin-based applications
- Netty 4.2 for reactive stacks
- GraalVM 25 for native-image support

Only `jakarta.annotation` and `jakarta.inject` annotations are supported.
`javax.annotation` and `javax.inject` are no longer recognized.

## Spring Framework 7.0 API changes

Spring Framework 7.0 removed these APIs entirely:

- `spring-jcl` module (replaced by Apache Commons Logging 1.3.0)
- `javax.annotation` and `javax.inject` annotation support
- `ListenableFuture` (use `CompletableFuture`)
- `OkHttp3` support
- `Theme` support
- Undertow-specific classes (Undertow does not support Servlet 6.1)
- Changed path mapping options (`suffixPatternMatch`, `trailingSlashMatch`, `favorPathExtension`, `matchOptionalTrailingSeparator`)
- `org.webjars:webjars-locator-core` (use `webjars-locator-lite`)

Spring Framework 7.0 replacement targets:

- `RestTemplate` (changed in 7.0, will be `@Changed` in 7.1) -- use `RestClient`
- `PathMatcher` in Spring MVC (use `PathPattern`)
- JUnit 4 TestContext support (`SpringRunner`, `SpringClassRule`, `AbstractJUnit4SpringContextTests`, etc.)
- Jackson 2.x support (use Jackson 3.x)
- Kotlin script templating (JSR 223 removal planned by Kotlin)
- `org.springframework.web.servlet.view.document` and `view.feed` (XLS, RSS, PDF views)
- `HandlerMappingIntrospector` SPI

## Common path

The ordinary Spring Framework job is:

1. Choose the smallest Spring modules that match the needed capability.
2. Define the application wiring with Java configuration and explicit beans.
3. Control the environment, profiles, and externalized configuration explicitly.
4. Keep bean lifecycle, events, transactions, conversion, validation, and scheduling behavior explicit.
5. Add a focused TestContext-based or plain Spring test that proves the framework integration works.

## First safe commands

Start with the narrowest local TestContext-backed test that proves the wiring or HTTP layer you are changing.

```sh
./mvnw test -Dtest=AppConfigTests
```

```sh
./gradlew test --tests AppConfigTests
```

```sh
./mvnw test -Dtest=OrderControllerTests,ItemControllerTests
```

```sh
./gradlew test --tests OrderControllerTests --tests ItemControllerTests
```

## Module selection

Use only the Spring Framework modules the application actually needs.

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

<dependencies>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-context</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-tx</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-test</artifactId>
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

Prefer constructor injection and explicit bean graphs.
Start with explicit `@Bean` wiring before reaching for broader framework indirection.

### Programmatic bean registration

Use `BeanRegistrar` when multiple beans must be registered conditionally from a single registration point, or when logic beyond a simple `@Bean` method is required:

```java
class ServiceBeanRegistrar implements BeanRegistrar {
    @Override
    void register(BeanRegistrationContext context) {
        context.registerBean(InventoryService.class);
        context.registerBean(ShippingService.class, config -> config.primary());
    }
}
```

Declare via `@Configuration` import or `ImportRegistrar`.
Use this only when standard `@Bean` methods cannot express the needed registration logic.

### Proxy configuration

Spring Framework 7.0 defaults all proxy processors (including `@Async`) to CGLIB proxies, matching Spring Boot behavior.
Opt out for individual beans with `@Proxyable`:

```java
@Bean
@Proxyable(ProxyMode.INTERFACES)
PaymentService paymentService() {
    return new PaymentServiceImpl();
}
```

Open [references/container-extension-scopes.md](references/container-extension-scopes.md) when the task needs a custom scope, container extension point, or clarification of `@Configuration` lite mode.

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

Use the environment to externalize configuration and profiles to control which beans or configurations are active.
Keep resource loading explicit when the application needs files from the classpath or filesystem.

## Bean scopes

| Scope | Use when |
| --- | --- |
| `singleton` | one shared instance per container (default) |
| `prototype` | new instance every time the bean is requested |
| custom scope | lifecycle is neither singleton nor prototype and requires explicit scope registration |

```java
@Bean
@Scope("prototype")
MyPrototypeBean prototypeBean() {
    return new MyPrototypeBean();
}
```

Use singleton by default.
Reach for prototype only when the lifecycle difference genuinely matters.
Web-specific scopes belong to web-focused configurations rather than the ordinary framework-core path.

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

Use lifecycle hooks only when initialization or shutdown semantics genuinely matter.
Prefer one lifecycle style consistently instead of mixing `@PostConstruct` / `@PreDestroy` with `initMethod` / `destroyMethod` in the same component graph.

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

Use application events for genuinely decoupled follow-up work, not as a substitute for basic method calls.
Keep event classes immutable and scoped to the application package.

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

Use `DataBinder` when the framework must bind incoming values onto an object.
Add custom converters only when the framework does not provide the needed conversion.

### SpEL expressions

SpEL supports null-safe operations and Elvis-operator unwrapping on `Optional` types in Spring Framework 7.0:

```java
@Value("#{order.customer?.name ?: 'anonymous'}")
String customerName;
```

SpEL expression evaluation is capped at 10,000 operations by default (Spring Framework 7.0.8).
Override via `SpelParserConfiguration(maxOperations, ...)` or the property `spring.expression.maxOperations`.

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

Validate at the boundary where input enters the application.
Use standard Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`) on input objects, and register method-validation infrastructure explicitly when service-layer method validation is part of the ordinary path.

## Servlet MVC

Enable the MVC infrastructure with a configuration class and a `DispatcherServlet` registration, or let a servlet container initializer wire both together.
The controller examples below assume the infrastructure is already in place and focus on controller shape.

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

Keep controllers thin: delegate all logic to services.
Use `@RestControllerAdvice` as a single exception boundary rather than scattering `try/catch` blocks across controllers.

### API versioning

Spring MVC and WebFlux provide first-class API versioning.
Resolve versions from URI path, header, or parameter, and mark changed versions:

```java
@RestController
@RequestMapping("/orders")
class OrderController {
    @GetMapping(produces = "application/vnd.example.v2+json")
    Order get(@PathVariable Long id) {
        return orders.findById(id);
    }
}
```

On the client side, set the API version on `RestClient` and `WebClient` requests.
`MockMvc` and `WebTestClient` also support versioning assertions.

### HTTP interface clients

Use `@ImportHttpServices` to batch-register HTTP interface client proxies by group:

```java
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "order", types = {OrderClient.class, OrderAdminClient.class})
static class HttpServicesConfiguration extends AbstractHttpServiceRegistrar {
    @Bean
    RestClientHttpServiceGroupConfigurer groupConfigurer() {
        return groups -> groups.forEachClient((group, builder) -> builder.defaultHeader("User-Agent", "My-App"));
    }
}
```

### Message converter configuration

Override `WebMvcConfigurer.configureMessageConverters(List<HttpMessageConverter<?>>)` to register converters explicitly.
Spring Framework 7 ships a Jackson 3 `JacksonJsonHttpMessageConverter`:

```java
@Override
public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
    converters.add(new JacksonJsonHttpMessageConverter(customMapper));
}
```

## Reactive HTTP

Add `spring-webflux` and use annotated controllers returning `Mono` and `Flux`.
The examples below assume WebFlux infrastructure is already configured and focus on controller shape.

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

Keep operator chains short.
Return early by flatMapping into the service rather than blocking.

## WebClient

In plain Spring Framework, register the builder explicitly as shown below.

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
    .onStatus(HttpStatusCode::is4xxClientError, response -> response.bodyToMono(String.class).map(ApiException::new))
    .onStatus(HttpStatusCode::is5xxServerError, response -> response.bodyToMono(String.class).map(UpstreamServiceException::new))
    .bodyToMono(Order.class);
```

Use `bodyToFlux` for streaming responses and `ExchangeStrategies` when the default codec buffer limit needs adjustment.

Keep client configuration explicit.
Centralize base URL and default headers in the bean definition rather than scattering them across call sites.

Open [references/webclient-reactive-depth.md](references/webclient-reactive-depth.md) when the task needs client filters, Reactor Netty-specific timeouts, retry behavior, or deeper reactive-chain patterns.

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

Keep transaction boundaries on service methods that own one business unit of work.
Avoid transactions that span multiple unrelated operations.

## Resilience

Spring Framework 7.0 includes built-in retry support in `spring-core`, replacing standalone Spring Retry for most use cases.
Enable with `@EnableResilientMethods`:

```java
@Configuration
@EnableResilientMethods
class ResilienceConfig {
}
```

### `@Retryable`

```java
@Service
class OrderClient {
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    Order fetchOrder(Long id) {
        return remoteService.getOrder(id);
    }
}
```

`@Retryable` automatically adapts to reactive return types (`Mono`, `Flux`), decorating the pipeline with Reactor's retry capabilities.
Imperative methods use `RetryTemplate` under the hood.

### `RetryTemplate`

For programmatic retry control:

```java
RetryTemplate retry = RetryTemplate.builder()
    .maxAttempts(3)
    .retryOn(OrderServiceUnavailableException.class)
    .exponentialBackoff(1000, 2, 10000)
    .build();
Order result = retry.execute(ctx -> remoteService.getOrder(id));
```

### `@ConcurrencyLimit`

Throttle concurrent method invocations:

```java
@Service
class ResourceConsumer {
    @ConcurrencyLimit(5)
    void process(Resource resource) {
    }
}
```

Use `@Retryable` on methods that call external services prone to transient failure.
Use `@ConcurrencyLimit` when a shared resource has a hard concurrency ceiling.
Combine both annotations on the same method when the application needs both retry and throttling.

## Plain JDBC and DataSource

### DataSource registration

```java
@Configuration
class DataConfig {
    @Bean
    DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.hsqldb.jdbcDriver");
        dataSource.setUrl("jdbc:hsqldb:hsql://localhost:");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }
}
```

Use `DriverManagerDataSource` or `SimpleDriverDataSource` only for testing and stand-alone environments.
Pair plain Spring JDBC with a real pool such as HikariCP or Apache DBCP2 when the application manages its own production data source.

### JdbcTemplate wiring

```java
@Configuration
class DataConfig {
    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

Query a single row:

```java
@Service
class InventoryRepository {
    private final JdbcTemplate jdbc;

    InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Item findById(Long id) {
        return jdbc.queryForObject("SELECT id, name, quantity FROM items WHERE id = ?", (rs, rowNum) -> new Item(rs.getLong("id"), rs.getString("name"), rs.getInt("quantity")), id);
    }
}
```

Update with parameters:

```java
void updateQuantity(Long id, int quantity) {
    jdbc.update("UPDATE items SET quantity = ? WHERE id = ?", quantity, id);
}
```

Batch operations:

```java
void insertBatch(List<Item> items) {
    jdbc.batchUpdate("INSERT INTO items (name, quantity) VALUES (?, ?)", items.stream().map(item -> new Object[]{item.name(), item.quantity()}).toList());
}
```

Keep JDBC templates as the data-access primitive when the application needs plain SQL without an ORM layer.
Use `NamedParameterJdbcTemplate` when named parameters improve readability over positional `?` placeholders.
Use `JdbcClient` (from Spring Framework 6.1+) for a fluent alternative that supports statement-level settings such as fetch size, max rows, and query timeout:

```java
List<Item> items = jdbcClient.sql("SELECT id, name, quantity FROM items")
    .fetchSize(500)
    .query()
    .list((rs, rowNum) -> new Item(rs.getLong("id"), rs.getString("name"), rs.getInt("quantity")));
```

Open [references/plain-jdbc-wiring.md](references/plain-jdbc-wiring.md) when the task needs transaction-scoped connections, `SqlRowSet`, `RowMapper` reuse, or `DataSourceTransactionManager` with plain JDBC.

## Async and scheduling

### Enable async processing

```java
@Configuration
@EnableAsync
class AppConfig {
    @Bean
    TaskExecutor taskExecutor() {
        return new SimpleAsyncTaskExecutor();
    }
}
```

Async method:

```java
@Component
class InventoryNotifier {
    @Async
    void sendAlert(String itemId) {
    }
}
```

Use `@EnableAsync` to activate framework-managed async execution.
The default `SimpleAsyncTaskExecutor` creates a new thread per call.
Register a custom `TaskExecutor` bean when pooled thread behavior, queue depth, or naming strategy matters.

### Enable scheduling

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

Use `@EnableScheduling` to activate framework-scheduled tasks.
Keep scheduled jobs idempotent and document the cron expression.

### Executor registration pointers

| Executor type | Use when |
| --- | --- |
| `SimpleAsyncTaskExecutor` | default async; each call gets a new thread |
| `ThreadPoolTaskExecutor` | pooled threads with queue; most common choice |
| `ThreadPoolTaskScheduler` | for `@Scheduled` methods that need a dedicated scheduler pool |
| `TaskExecutor` interface | `execute(Runnable)` abstraction for swapping executor implementations |

```java
@Bean
TaskExecutor threadPoolTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("inventory-");
    executor.initialize();
    return executor;
}
```

Open [references/async-executor-registration.md](references/async-executor-registration.md) when the task needs async exception handling, completion coordination with `Future` / `CompletableFuture`, or custom `TaskDecorator` for ThreadLocal propagation.

Do not stack `@Async` and `@Scheduled` on the same method casually.
Treat that combination as a proxy and executor design decision rather than ordinary scheduling.
Separate concerns into distinct methods when both behaviors are needed.

## Cache and AOT escalation

Use `@EnableCaching` only when the task explicitly needs framework-managed cache annotations and the cache boundary is clear.

Treat AOT and native-image hints as an escalation point rather than part of the ordinary path.
Keep the default implementation reflective and explicit until the task specifically requires AOT-friendly wiring.

GraalVM 25 introduces the unified reachability metadata format.
Resource hints now use glob patterns instead of regex: `"/files/*.ext"` matches only direct children, not subdirectories.
Use `"/files/**/*.ext"` for recursive matching.
Registering a reflection hint for a type now implies methods, constructors, and fields introspection; `MemberCategory.DECLARED_FIELDS` is changed in favor of a simple `hints.reflection().registerType(MyType.class)`.

## AOP escalation

Open [references/aop-cross-cutting.md](references/aop-cross-cutting.md) when cross-cutting behavior must wrap many beans consistently and the ordinary bean wiring path is not enough.

## Container extension escalation

Open [references/container-extension-scopes.md](references/container-extension-scopes.md) for `BeanFactoryPostProcessor`, `BeanPostProcessor`, custom scope registration, or advanced listener infrastructure that goes beyond what `SKILL.md` covers.

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

Test the smallest framework integration that proves the behavior.
Use `@ExtendWith(SpringExtension.class)` to integrate the Spring `ApplicationContext` with JUnit Jupiter.

### Test context pausing

Spring Framework 7.0 pauses unused application contexts in the test context cache to stop background processes.
Configure via `spring.test.context.cache.pause` (`smart` default, `always`, or `never`):

```java
@SpringBootTest(properties = "spring.test.context.cache.pause=never")
class OrderServiceTests {
}
```

### `@Nested` test hierarchies

`SpringExtension` supports dependency injection into `@Nested` test class constructors and fields.
If custom `TestExecutionListener` implementations break after upgrading, use `testContext.getTestInstance().getClass()` instead of `testContext.getTestClass()` for lookups.

### `RestTestClient`

`RestTestClient` provides a non-reactive variant of `WebTestClient` for servlet-based tests with fluent API and nice assertions:

```java
RestTestClient client = RestTestClient.bindToApplicationContext(ctx).build();
client.get().uri("/orders/1").exchange()
    .expectStatus().isOk()
    .expectBody();
```

### Bean overrides for non-singleton beans

`@MockitoBean`, `@MockitoSpyBean`, and `@TestBean` can now target non-singleton beans including `prototype` and custom scopes.

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

Keep controller tests focused on HTTP semantics: status codes, headers, and response shape.
Delegate business-logic assertions to plain unit tests against the service layer.

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
- Use `RestClient` for new HTTP client code; avoid starting new usage of `RestTemplate`.
- `HttpHeaders` no longer extends `MultiValueMap` in 7.0. Use `HttpHeaders` methods directly instead of map operations.
- CORS pre-flight requests are no longer rejected when the CORS configuration is empty.
- `PathPattern` is the only supported pattern matcher for HTTP request mappings.
  - `AntPathMatcher` should not be used for HTTP request mappings.

## References

- Open [references/aop-cross-cutting.md](references/aop-cross-cutting.md) when the task needs framework-level AOP beyond ordinary bean wiring.
- Open [references/async-executor-registration.md](references/async-executor-registration.md) when the task needs async exception handling, completion coordination with `Future` / `CompletableFuture`, or `TaskDecorator` for ThreadLocal propagation.
- Open [references/aspectj-ltw.md](references/aspectj-ltw.md) when the task needs load-time weaving, `@Configurable`, or AspectJ join points beyond Spring AOP proxies.
- Open [references/container-extension-scopes.md](references/container-extension-scopes.md) when the blocker is container extension points, custom scopes, advanced listener infrastructure, or `@Configuration` lite-mode behavior.
- Open [references/environment-and-resources.md](references/environment-and-resources.md) when the task needs deeper control over profiles, property sources, or resource resolution beyond the common path.
- Open [references/plain-jdbc-wiring.md](references/plain-jdbc-wiring.md) when the task needs transaction-scoped connections, `SqlRowSet`, `RowMapper` reuse, or `DataSourceTransactionManager` with plain JDBC.
- Open [references/property-binding-conversion-validation.md](references/property-binding-conversion-validation.md) when the task needs advanced data-binding rules, formatter/converter registration, or validation groups beyond the common path.
- Open [references/webclient-reactive-depth.md](references/webclient-reactive-depth.md) when the task needs WebClient filters, transport-specific timeouts, retry selection, or deeper reactive pipeline behavior.
