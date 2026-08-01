---
name: spring-framework
description: >-
  Apply core Spring Framework APIs for container configuration, bean lifecycle, transactions, events, validation, scheduling, async execution, resilience, JDBC, and TestContext support.
  Use when configuring `@Configuration` classes, managing scopes and lifecycle callbacks, setting transaction boundaries, enabling resilient methods, registering beans programmatically, or wiring framework infrastructure without Boot autoconfiguration.
---

# Spring Framework

## Boundaries

Use this skill for core Spring Framework APIs: container behavior, bean wiring, lifecycle hooks, transactions, events, scheduling, property binding, conversion, validation, resilience, plain JDBC, and TestContext-driven framework tests.

HTTP controllers and clients, security policy, and Boot auto-configuration are separate application boundaries rather than container-core work.

## Baseline requirements

Spring Framework 7 requires:

- JDK 17 minimum; JDK 25 recommended as the latest LTS
- Jakarta EE 11 (Servlet 6.1, JPA 3.2, Bean Validation 3.1)
- JUnit 6 (JUnit 6 is the target test line)
- Kotlin 2.2 for Kotlin-based applications
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
- `org.webjars:webjars-locator-core` (use `webjars-locator-lite`)

Spring Framework 7.0 replacement targets:

- JUnit 4 TestContext support (`SpringRunner`, `SpringClassRule`, `AbstractJUnit4SpringContextTests`, etc.)
- Jackson 2.x support (use Jackson 3.x)
- Kotlin script templating (JSR 223 removal planned by Kotlin)

## Common path

The ordinary Spring Framework job is:

1. Choose the smallest Spring modules that match the needed capability.
2. Define the application wiring with Java configuration and explicit beans.
3. Control the environment, profiles, and externalized configuration explicitly.
4. Keep bean lifecycle, events, transactions, conversion, validation, and scheduling behavior explicit.
5. Add a focused TestContext-based or plain Spring test that proves the framework integration works.

## First safe commands

Start with the narrowest local TestContext-backed test that proves the wiring you are changing.

```sh
./mvnw test -Dtest=AppConfigTests
```

```sh
./gradlew test --tests AppConfigTests
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

Add `spring-jdbc` or other framework modules only when the task truly needs them.

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
@Configuration
@Import(MyBeanRegistrar.class)
class MyConfiguration {
}

class MyBeanRegistrar implements BeanRegistrar {
    @Override
    public void register(BeanRegistry registry, Environment env) {
        registry.registerBean(InventoryService.class);
        if (env.matchesProfiles("shipping")) {
            registry.registerBean(ShippingService.class);
        }
    }
}
```

Use this only when standard `@Bean` methods cannot express the needed registration logic.

### Proxy configuration

Core Spring uses interface-based JDK proxies when the target implements an interface and CGLIB proxies otherwise.
Spring Boot may enable class-based proxies globally through its `spring.aop.proxy-target-class` configuration.
Override the global choice for an individual bean with `@Proxyable`:

```java
@Bean
@Proxyable(ProxyType.INTERFACES)
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
Request-bound scopes belong to HTTP-layer configuration rather than the ordinary framework-core path.

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
    @Retryable(maxRetries = 3, delay = 1000, multiplier = 2, maxDelay = 10000)
    Order fetchOrder(Long id) {
        return remoteService.getOrder(id);
    }
}
```

`@Retryable` supports imperative and reactive methods, with `maxRetries`, `delay`, `jitter`, `multiplier`, `maxDelay`, `includes`, `excludes`, and `predicate` available for retry policy configuration.

`RetryTemplate` is a separate programmatic API for callers that need to construct and invoke a retry policy directly:

```java
RetryPolicy policy = RetryPolicy.builder()
    .maxRetries(3)
    .delay(Duration.ofSeconds(1))
    .multiplier(2)
    .maxDelay(Duration.ofSeconds(10))
    .build();
RetryTemplate retry = new RetryTemplate(policy);
Order result = retry.invoke(() -> remoteService.getOrder(id));
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
        dataSource.setUrl("jdbc:hsqldb:hsql://localhost/test");
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
    .withFetchSize(500)
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
The default pause mode is `on_context_switch`.
Configure `spring.test.context.cache.pause` with `always`, `on_context_switch`, or `never`.

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AppConfig.class)
@TestPropertySource(properties = "spring.test.context.cache.pause=never")
class OrderServiceTests {
}
```

### `@Nested` test hierarchies

`SpringExtension` supports dependency injection into `@Nested` test class constructors and fields.
If custom `TestExecutionListener` implementations break after upgrading, use `testContext.getTestInstance().getClass()` instead of `testContext.getTestClass()` for lookups.

### Bean overrides for non-singleton beans

`@MockitoBean`, `@MockitoSpyBean`, and `@TestBean` can now target non-singleton beans including `prototype` and custom scopes.

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
- Verify framework tests stay narrow enough to isolate the intended integration behavior.

## Production checklist

- Keep bean wiring explicit enough that startup failures remain diagnosable.
- Avoid transaction boundaries broader than one business unit of work.
- Use lifecycle hooks sparingly and document why they are needed.
- Keep application events purposeful rather than turning them into hidden control flow.
- Treat Spring integration tests as part of the framework compatibility surface.

## References

- Open [references/aop-cross-cutting.md](references/aop-cross-cutting.md) when the task needs framework-level AOP beyond ordinary bean wiring.
- Open [references/async-executor-registration.md](references/async-executor-registration.md) when the task needs async exception handling, completion coordination with `Future` / `CompletableFuture`, or `TaskDecorator` for ThreadLocal propagation.
- Open [references/aspectj-ltw.md](references/aspectj-ltw.md) when the task needs load-time weaving, `@Configurable`, or AspectJ join points beyond Spring AOP proxies.
- Open [references/container-extension-scopes.md](references/container-extension-scopes.md) when the blocker is container extension points, custom scopes, advanced listener infrastructure, or `@Configuration` lite-mode behavior.
- Open [references/environment-and-resources.md](references/environment-and-resources.md) when the task needs deeper control over profiles, property sources, or resource resolution beyond the common path.
- Open [references/plain-jdbc-wiring.md](references/plain-jdbc-wiring.md) when the task needs transaction-scoped connections, `SqlRowSet`, `RowMapper` reuse, or `DataSourceTransactionManager` with plain JDBC.
- Open [references/property-binding-conversion-validation.md](references/property-binding-conversion-validation.md) when the task needs advanced data-binding rules, formatter/converter registration, or validation groups beyond the common path.
