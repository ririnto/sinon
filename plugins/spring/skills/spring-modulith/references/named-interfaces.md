# Spring Modulith named interfaces

Open this reference when module exposure rules need explicit `allowedDependencies` or several named interface packages.

## Module dependency rule shape

```java
@ApplicationModule(allowedDependencies = "inventory::api")
package example.orders;
```

Use explicit allowed dependencies when package structure alone is not enough to express which neighboring modules may be called.

## Package-level named interface shape

```java
@NamedInterface("api")
package example.orders.api;
```

Use this shape when one module exposes several named interface packages and other modules may depend on only one of them.

## Consuming-module shape

```java
@ApplicationModule(allowedDependencies = "orders::api")
package example.inventory;
```

```java
package example.orders.api;

public interface OrderLookup {
    OrderSummary findById(String orderId);
}
```

## Multi-interface package shape

```text
example.orders.api
example.orders.spi
example.orders.internal
```

## Decision points

| Situation | Use |
| --- | --- |
| Module should expose only one API subset | named interface |
| Only specific neighbor modules may be referenced | `allowedDependencies` |
| Ordinary package conventions are already enough | stay on the common path |

## Verification rule

Verify `ApplicationModules.of(Application.class).verify()` fails when a consuming module reaches into `internal` packages instead of the named interface.
