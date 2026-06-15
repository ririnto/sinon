# Spring Integration control bus and system management

Open this reference when the application must inspect, start, stop, or observe Integration endpoints in production.

## Control bus shape

```java
IntegrationFlow.from("controlBus.input")
    .controlBus()
    .get()
```

## IntegrationPatternType: message_store

`MessageStore` now implements `IntegrationPattern` with a default `message_store` type, making message stores visible in the integration graph and monitoring systems (Prometheus, Datadog).
Existing systems using message stores will now show the `message_store` label in monitoring dashboards.

## Observability shape

```java
@EnableIntegrationManagement(observationPatterns = "*")
class IntegrationManagementConfiguration {
}
```

## Integration graph shape

```java
IntegrationGraphServer graphServer = context.getBean(IntegrationGraphServer.class);
graphServer.rebuild();
Graph graph = graphServer.getGraph();
```

## Control bus command shape

```java
gateway.send(new GenericMessage<>("@'fileInboundAdapter'.stop()"));
```

## Operational rules

- Use the control bus for explicit endpoint lifecycle operations, not as a replacement for normal business messaging.
- Keep message history, metrics, and interceptor-based tracing enabled before debugging production routing issues.
- Inspect the integration graph before changing endpoint code when the system already contains dynamic or conditional flows.

## Verification rule

Verify one production-facing management test can inspect the graph and stop or start the intended endpoint without affecting unrelated flows.
