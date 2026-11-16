# Container, beans, events, and validation

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
    /**
     * Registers beans programmatically, conditionally on the active environment profiles.
     */
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

Open [references/container-extension-scopes.md](container-extension-scopes.md) when the task needs a custom scope, container extension point, or clarification of `@Configuration` lite mode.

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
Reach for prototype only when the lifecycle difference matters.
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
    /**
     * Runs the warmup once the application context is fully refreshed.
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
    }
}
```

Use lifecycle hooks only when initialization or shutdown semantics matter.
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

    /**
     * Places the order and publishes the corresponding application event.
     */
    public void place(Order order) {
        events.publishEvent(new OrderPlacedEvent(this, order));
    }
}
```

Listen to events:

```java
@Component
class OrderNotificationListener implements ApplicationListener<OrderPlacedEvent> {
    /**
     * Reacts to one published order placement event.
     */
    @Override
    public void onApplicationEvent(OrderPlacedEvent event) {
    }
}
```

Use application events for decoupled follow-up work, not as a substitute for basic method calls.
Keep event classes immutable and scoped to the application package.

Open [references/container-extension-scopes.md](container-extension-scopes.md) when the task depends on ordered listeners, `@EventListener` conditions, or lower-level listener infrastructure.

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
