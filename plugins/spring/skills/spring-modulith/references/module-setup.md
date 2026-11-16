# Module setup

## First safe configuration

## First safe commands

```sh
./mvnw test -Dtest=ModularityTests
```

```sh
./gradlew test --tests ModularityTests
```

## Application class shape

```java
@org.springframework.modulith.Modulithic
@SpringBootApplication
class Application {
}
```

Use `@org.springframework.modulith.Modulithic` to define the application module arrangement.
Runtime metadata, startup verification, and dependency-ordered `ApplicationModuleInitializer` execution additionally require `spring-modulith-runtime` and:

```properties
spring.modulith.runtime.verification-enabled=true
```

Set `systemName` for human-readable documentation names.

## Boundary verification shape

```java
ApplicationModules.of(Application.class).verify();
```

Exclude packages from inspection when needed:

```java
ApplicationModules.of(Application.class,
    JavaClass.Predicates.resideInAPackage("com.example.db")).verify();
```

## Module event publication shape

```java
events.publishEvent(new OrderCompleted(order.id()));
```

## Module listener shape

```java
@Component
class InventoryProjection {
    @ApplicationModuleListener
    void on(OrderCompleted event) {
    }
}
```

Use `@ApplicationModuleListener` when the listener should clearly belong to the cross-module event boundary.
The annotation is syntactic sugar for `@Transactional(propagation = REQUIRES_NEW) @TransactionalEventListener`.

## Package declaration shape

```java
@ApplicationModule
package example.orders;
```

```java
@NamedInterface("api")
package example.orders.api;
```

Use these `package-info.java` declarations when the module boundary and the exported named interface should be explicit in code rather than implied only by package names.

## Module detection strategy

By default, direct sub-packages of the main application class package are modules.
Switch to explicit annotation only:

```properties
spring.modulith.detection-strategy=explicitly-annotated
```

Or provide a custom `ApplicationModuleDetectionStrategy` implementation.

Use boundary verification for structural changes and module tests for interactions that need a Spring runtime.
