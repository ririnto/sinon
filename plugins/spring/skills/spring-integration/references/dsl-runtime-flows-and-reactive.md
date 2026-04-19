# Spring Integration DSL runtime flows and reactive patterns

Open this reference when the task needs runtime flow registration, subflows, reactive channels, or composed Java DSL flows.

## Runtime registration shape

```java
IntegrationFlowContext.IntegrationFlowRegistration registration = integrationFlowContext.registration(flow)
    .id("tenantFlow")
    .register();
```

## Reactive channel shape

```java
MessageChannel reactiveChannel() {
    return new FluxMessageChannel();
}
```

## Subflow composition shape

```java
.route(OrderCommand::type, mapping -> mapping
    .subFlowMapping(OrderType.STANDARD, sf -> sf.channel("orders.standard"))
    .subFlowMapping(OrderType.EXPRESS, sf -> sf.channel("orders.express")))
```

## Decision points

- Register flows at runtime only when tenants, routes, or external bindings truly change after startup.
- Use subflows to keep one large graph readable without scattering the contract across unrelated beans.
- Use reactive channels only when the surrounding source and sink are already reactive.
