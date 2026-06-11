---
name: spring-modulith
description: >-
  Structure Spring Boot applications as explicit application modules with boundary verification, published module events, and module-interaction tests via Spring Modulith. Use when defining module boundaries, verifying encapsulation with the Modulith test runner, publishing events across modules, or configuring event publication mode.
---

# Spring Modulith

## Boundaries

Use `spring-modulith` for package-level application module boundaries, named interfaces, module verification, module events, and module-focused tests.

- Use plain package refactoring or Java language guidance when the task is only about naming or moving classes without a module-boundary policy.
- Keep this skill focused on modular monolith structure inside one deployable application, not on splitting services across repositories.

## Common path

The ordinary Spring Modulith job is:

1. Identify the application modules and their public named interfaces first.
2. Keep dependencies flowing only through allowed module boundaries.
3. Publish application events for cross-module collaboration instead of direct internal calls where decoupling matters.
4. Add a verification test that fails when the module structure drifts.
5. Add at least one module-focused integration test for a meaningful interaction path.

## Module decisions

| Situation | Use |
| --- | --- |
| Other modules may use only a subset of types | named interface |
| Cross-module reaction should happen after work completes | application event |
| Boundary drift must fail fast in CI | `ApplicationModules.verify()` |
| One module interaction needs isolated integration coverage | `@ApplicationModuleTest` |
| Module arrangement needs runtime metadata or startup verification | `@Modulithic` |
| Events must survive listener failures | event publication registry |
| Events must be reliably forwarded to external systems | event outbox |
| Domain reacts to calendar boundaries | Moments |
| Application needs modular Spring Boot auto-configuration slicing | Module Slicing |

Keep named interfaces small and intention-revealing. Prefer events over direct internal bean calls when the interaction does not need immediate synchronous coupling.

When one module may depend on only specific neighbors, make that dependency rule explicit rather than relying on package conventions alone.

## Dependency baseline

Import the BOM and use the core and test starters for the common path.

The current GA Spring Modulith BOM is `2.1.0`. It is compiled against Spring Boot 4.1.x and tested against Boot 4.1.x.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>2.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

```groovy
dependencyManagement {
    imports {
        mavenBom 'org.springframework.modulith:spring-modulith-bom:2.1.0'
    }
}

dependencies {
    implementation 'org.springframework.modulith:spring-modulith-starter-core'
    testImplementation 'org.springframework.modulith:spring-modulith-starter-test'
}
```

## First safe configuration

### First safe commands

```sh
./mvnw test -Dtest=ModularityTests
```

```sh
./gradlew test --tests ModularityTests
```

### Application class shape

```java
@Modulithic
@SpringBootApplication
class Application {
}
```

Use `@Modulithic` to enable runtime metadata, startup verification, and `ApplicationModuleInitializer` ordering. Set `systemName` for human-readable documentation names.

### Boundary verification shape

```java
ApplicationModules.of(Application.class).verify();
```

Exclude packages from inspection when needed:

```java
ApplicationModules.of(Application.class,
    JavaClass.Predicates.resideInAPackage("com.example.db")).verify();
```

### Module event publication shape

```java
events.publishEvent(new OrderCompleted(order.id()));
```

### Module listener shape

```java
@Component
class InventoryProjection {
    @ApplicationModuleListener
    void on(OrderCompleted event) {
    }
}
```

Use `@ApplicationModuleListener` when the listener should clearly belong to the cross-module event boundary. The annotation is syntactic sugar for `@Async @Transactional(propagation = REQUIRES_NEW) @TransactionalEventListener`.

### Package declaration shape

```java
@ApplicationModule
package example.orders;
```

```java
@NamedInterface("api")
package example.orders.api;
```

Use these `package-info.java` declarations when the module boundary and the exported named interface should be explicit in code rather than implied only by package names.

### Module detection strategy

By default, direct sub-packages of the main application class package are modules. Switch to explicit annotation only:

```properties
spring.modulith.detection-strategy=explicitly-annotated
```

Or provide a custom `ApplicationModuleDetectionStrategy` implementation.

Start by making verification pass before adding richer module test scenarios.

## Coding procedure

1. Name modules after business capabilities, not technical layers.
2. Keep non-exported types package-private wherever practical so module boundaries are reinforced by code structure.
3. Use named interfaces to expose only the entry points other modules are allowed to depend on.
4. Publish application events when one module should react after another module completes work.
5. Add `@ApplicationModuleTest` only after ordinary boundary verification is already clean.
6. Treat boundary violations as architecture regressions, not optional warnings.

## Module test decisions

| Situation | Use |
| --- | --- |
| One module should run in isolation | `@ApplicationModuleTest(mode = STANDALONE)` |
| Test should see direct dependency modules | `@ApplicationModuleTest(mode = DIRECT_DEPENDENCIES)` |
| Test should see the full dependency tree | `@ApplicationModuleTest(mode = ALL_DEPENDENCIES)` |
| Event publication must be asserted directly | `PublishedEvents` or `AssertablePublishedEvents` |
| Fluent assertions on published events | `AssertablePublishedEvents` |
| Efferent dependency should stay mocked in the module test | `@MockitoBean` |
| Event-driven interaction needs richer async verification | `Scenario` API |

## Implementation examples

### Boundary verification test

```java
class ModularityTests {
    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(Application.class).verify();
    }
}
```

### Module event publication

```java
@Service
class Orders {
    private final ApplicationEventPublisher events;

    Orders(ApplicationEventPublisher events) {
        this.events = events;
    }

    void complete(Order order) {
        order.complete();
        events.publishEvent(new OrderCompleted(order.id()));
    }
}
```

### Event listener in another module

```java
@Component
class InventoryProjection {
    private final InventoryReadModel readModel;

    InventoryProjection(InventoryReadModel readModel) {
        this.readModel = readModel;
    }

    @ApplicationModuleListener
    void on(OrderCompleted event) {
        readModel.markCompleted(event.orderId());
    }
}
```

### Module test shape

```java
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class OrdersModuleTest {
    @Autowired
    Orders orders;

    @Test
    void completesOrderAndPublishesModuleEvent(PublishedEvents publishedEvents) {
        orders.complete(new Order("o-1"));
        assertThat(publishedEvents.ofType(OrderCompleted.class)).hasSize(1);
    }
}
```

### Assertable published events test shape

```java
@ApplicationModuleTest
class OrdersModuleTest {
    @Autowired
    Orders orders;

    @Test
    void publishesOrderCompleted(AssertablePublishedEvents events) {
        orders.complete(new Order("o-1"));
        assertThat(events)
            .contains(OrderCompleted.class)
            .matching(OrderCompleted::orderId, "o-1");
    }
}
```

### Named interface shape

```text
example.orders.api
example.orders.internal
```

```java
@ApplicationModule(allowedDependencies = "inventory::api")
package example.orders;
```

### Documentation generation shape

```java
class DocumentationTests {
    ApplicationModules modules = ApplicationModules.of(Application.class);

    @Test
    void writeDocumentationSnippets() {
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases()
            .writeAggregatingDocument();
    }
}
```

Generate C4 component diagrams, per-module diagrams, module canvases, and an aggregating Asciidoctor file. Output goes to `spring-modulith-docs` in the build folder by default.

## Testing checklist

- Verify module verification runs in CI and fails on illegal dependencies.
- Verify public entry points are the only paths other modules use.
- Verify cross-module collaboration works through the intended event or named interface.
- Verify at least one module-focused integration test covers a real interaction path.
- Verify boundary tests stay green after refactors that move packages or listeners.

## Production checklist

- Keep module names, exported interfaces, and event types stable after other modules depend on them.
- Avoid leaking internal packages as informal public APIs.
- Keep cross-module synchronous calls rare and intentional.
- Treat verification failures as release blockers for architecture-sensitive applications.
- Use documentation generation or runtime inspection only after the underlying boundaries are already correct.

## References

- Open [references/named-interfaces.md](references/named-interfaces.md) when module exposure rules need explicit `allowedDependencies` or several named interface packages.
- Open [references/scenario-tests.md](references/scenario-tests.md) when event-driven module tests need richer `Scenario` verification than the ordinary module test path.
- Open [references/event-publication-registry.md](references/event-publication-registry.md) when module events must be tracked, replayed, or completed reliably after failures.
- Open [references/moments.md](references/moments.md) when the application reacts to business-relevant time events such as day, week, or month boundaries.
- Open [references/event-externalization.md](references/event-externalization.md) when module events must be published to external brokers (Kafka, AMQP, JMS) or forwarded via the outbox pattern (Namastack, JobRunr).
- Open [references/documentation-generation.md](references/documentation-generation.md) when module arrangement diagrams and canvases must be generated for developer documentation.
- Open [references/runtime-support.md](references/runtime-support.md) when the application needs startup verification, module initializer ordering, module-specific Flyway migrations, actuator endpoints, observability, change-aware test execution, or Module Slicing.
