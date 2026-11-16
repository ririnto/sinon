# Cache, AOT, and TestContext

## Cache and AOT escalation

Use `@EnableCaching` only when the task explicitly needs framework-managed cache annotations and the cache boundary is clear.

Treat AOT and native-image hints as an escalation point rather than part of the ordinary path.
Keep the default implementation reflective and explicit until the task specifically requires AOT-friendly wiring.

GraalVM 25 introduces the unified reachability metadata format.
Resource hints now use glob patterns instead of regex: `"/files/*.ext"` matches only direct children, not subdirectories.
Use `"/files/**/*.ext"` for recursive matching.
Registering a reflection hint for a type now implies methods, constructors, and fields introspection.
`MemberCategory.DECLARED_FIELDS` is replaced by a simple `hints.reflection().registerType(MyType.class)`.

## AOP escalation

Open [references/aop-cross-cutting.md](aop-cross-cutting.md) when cross-cutting behavior must wrap many beans consistently and the ordinary bean wiring path is not enough.

## Container extension escalation

Open [references/container-extension-scopes.md](container-extension-scopes.md) for `BeanFactoryPostProcessor`, `BeanPostProcessor`, custom scope registration, or advanced listener infrastructure that goes beyond what `SKILL.md` covers.

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
