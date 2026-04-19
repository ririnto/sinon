---
name: "spring-framework"
description: "Use this skill when the task depends on core Spring Framework APIs rather than Boot conventions, especially the container, Java configuration, bean lifecycle, transactions, events, validation, and TestContext support."
metadata:
  title: "Spring Framework"
  official_project_url: "https://spring.io/projects/spring-framework"
  reference_doc_urls:
    - "https://docs.spring.io/spring-framework/reference/index.html"
    - "https://docs.spring.io/spring-framework/reference/core.html"
    - "https://docs.spring.io/spring-framework/reference/testing.html"
  version: "7.0.7"
---

Use this skill when the task depends on core Spring Framework APIs rather than Boot conventions, especially the container, Java configuration, bean lifecycle, transactions, events, validation, and TestContext support.

## Boundaries

Use `spring-framework` for low-level Spring container behavior, bean wiring, lifecycle hooks, transactions, events, scheduling, property binding, conversion, validation, and TestContext-driven framework tests.

Use narrower guidance when the task is primarily about servlet MVC, reactive HTTP, security, or Boot auto-configuration rather than framework-core building blocks.

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
- Open [references/container-extension-scopes.md](references/container-extension-scopes.md) when the blocker is container extension points, custom scopes, advanced listener infrastructure, or `@Configuration` lite-mode behavior.
- Open [references/environment-and-resources.md](references/environment-and-resources.md) when the task needs deeper control over profiles, property sources, or resource resolution beyond the common path.
- Open [references/property-binding-conversion-validation.md](references/property-binding-conversion-validation.md) when the task needs advanced data-binding rules, formatter/converter registration, or validation groups beyond the common path.
