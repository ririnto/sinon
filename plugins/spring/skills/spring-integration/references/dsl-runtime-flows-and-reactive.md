# Spring Integration DSL runtime flows and reactive patterns

Open this reference when the task needs runtime flow registration, subflows, reactive channels, composed Java DSL flows, or DSL-only mode configuration.

## DSL-only mode

Set `spring.integration.annotations.enable=false` to disable messaging annotation post-processor registration. When disabled, `MessagingAnnotationPostProcessor` and `MessagingAnnotationBeanPostProcessor` are not registered, reducing overhead in applications that use only Java DSL without annotation-driven endpoints (`@ServiceActivator`, `@Transformer`, `@Gateway`, etc.).

```properties
spring.integration.annotations.enable=false
```

Use this property only in pure DSL applications. Do not disable it when any annotation-driven messaging endpoints are present.

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
.route(OrderCommand::type, mapping -> mapping.subFlowMapping(OrderType.STANDARD, sf -> sf.channel("orders.standard")).subFlowMapping(OrderType.EXPRESS, sf -> sf.channel("orders.express")))
```

## Decision points

- Register flows at runtime only when tenants, routes, or external bindings truly change after startup.
- Use subflows to keep one large graph readable without scattering the contract across unrelated beans.
- Use reactive channels only when the surrounding source and sink are already reactive.
