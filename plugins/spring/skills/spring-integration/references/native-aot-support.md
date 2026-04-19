# Spring Integration native AOT support

Open this reference when the flow must run in a native image and adapter or reflection constraints become part of the design.

## AOT boundary

Treat native support as an explicit branch. Verify each adapter, gateway proxy, and reflection-heavy component before assuming the flow is safe for a native image.

## Decision rules

- Prefer straightforward Java DSL and explicit bean wiring.
- Re-check adapters that depend on reflection, proxies, or external client libraries.
- Keep runtime flow registration and other dynamic patterns out of the native path unless they are already proven in the target environment.

## Runtime hint shape

```java
@ImportRuntimeHints(IntegrationRuntimeHints.class)
class IntegrationNativeConfiguration {
}
```
