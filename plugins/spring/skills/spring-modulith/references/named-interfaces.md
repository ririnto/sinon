# Named Interfaces

Open this reference when module exposure rules need explicit `allowedDependencies` or several named interface packages.

## Module dependency rule shape

```java
@ApplicationModule(allowedDependencies = "inventory::api")
package example.orders;
```

## Package-level named interface shape

```java
@NamedInterface("api")
package example.orders.api;
```

## Consuming-module shape

```java
@ApplicationModule(allowedDependencies = "orders::api")
package example.inventory;
```

Consuming-module code can only reach into `orders::api` packages, not internal ones.

## Multi-interface package shape

```text
example.orders.api
example.orders.spi
example.orders.internal
```

```java
package example.orders.api;

public interface OrderLookup {
    OrderSummary findById(String orderId);
}
```

## Allowed-dependency wildcard

Allow access to all explicitly declared named interfaces of a module:

```java
@ApplicationModule(allowedDependencies = "orders::*")
package example.inventory;
```

## Decision points

| Situation | Use |
| --- | --- |
| Module should expose only one API subset | named interface on base package |
| Module should expose multiple named subsets | multiple `@NamedInterface` packages |
| Only specific neighbor modules may reference this one | `allowedDependencies` |
| Ordinary package conventions are enough | no annotation needed |

## Customizing named interface detection

Provide a custom `ApplicationModuleDetectionStrategy` and override `detectNamedInterfaces`:

```java
class CustomStrategy implements ApplicationModuleDetectionStrategy {
    @Override
    Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage) {
    }

    @Override
    NamedInterfaces detectNamedInterfaces(
            JavaPackage basePackage, ApplicationModuleInformation information) {
        return NamedInterfaces.builder()
            .recursive()
            .matching("api")
            .build();
    }
}
```

## Package-based named interfaces merge fix

A bug in versions before 2.1.0 caused package-based named interfaces (detected without explicit `@NamedInterface` annotation) to merge incorrectly when multiple modules declared interfaces with the same name segment.
This is fixed in 2.1.0.

## Verification rule

Verify `ApplicationModules.of(Application.class).verify()` fails when a consuming module reaches into `internal` packages or into named interfaces not listed in its `allowedDependencies`.
