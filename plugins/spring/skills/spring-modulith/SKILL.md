---
name: "spring-modulith"
description: "Use this skill when structuring a Spring Boot application as explicit application modules, verifying module boundaries, publishing module events, and testing module interactions with Spring Modulith."
metadata:
  title: "Spring Modulith"
  official_project_url: "https://spring.io/projects/spring-modulith"
  reference_doc_urls:
    - "https://docs.spring.io/spring-modulith/reference/index.html"
  version: "2.0.5"
---

Use this skill when structuring a Spring Boot application as explicit application modules, verifying module boundaries, publishing module events, and testing module interactions with Spring Modulith.

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

Keep named interfaces small and intention-revealing. Prefer events over direct internal bean calls when the interaction does not need immediate synchronous coupling.

When one module may depend on only specific neighbors, make that dependency rule explicit rather than relying on package conventions alone.

## Dependency baseline

Import the BOM and use the core and test starters for the common path.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>2.0.5</version>
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

## First safe configuration

### First safe commands

```bash
./mvnw test -Dtest=ModularityTests
```

```bash
./gradlew test --tests ModularityTests
```

### Boundary verification shape

```java
ApplicationModules.of(Application.class).verify();
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

Use `@ApplicationModuleListener` when the listener should clearly belong to the cross-module event boundary.

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
| One module should start with only its direct collaborators | `@ApplicationModuleTest(mode = STANDALONE)` |
| Test should see direct dependency modules too | `@ApplicationModuleTest(mode = DIRECT_DEPENDENCIES)` |
| Event publication must be asserted directly | `PublishedEvents` or `AssertablePublishedEvents` |
| Efferent dependency should stay mocked in the module test | `@MockitoBean` |

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
    @ApplicationModuleListener
    void on(OrderCompleted event) {
        // update read model or trigger follow-up work
    }
}
```

### Module test shape

```java
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class OrdersModuleTest {
}
```

### Published events test shape

```java
@ApplicationModuleTest
class OrdersModuleTest {
    @Test
    void publishesOrderCompleted(PublishedEvents events) {
    }
}
```

### Named interface shape

```text
example.orders.api
example.orders.internal
```

## Output and configuration shapes

### Verification call shape

```java
ApplicationModules.of(Application.class).verify();
```

### Module event shape

```java
new OrderCompleted(order.id())
```

### Module-focused test shape

```java
@ApplicationModuleTest
class OrdersModuleTest {
}
```

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
