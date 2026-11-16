# Health and observability

## Health and observability decisions

| Situation | Use |
| --- | --- |
| Server should report serving state | server health support |
| Actuator health should include selected gRPC services | `spring.grpc.server.health.service.<name>.include` |
| Client should stop calling an unhealthy upstream | per-channel client health checks |
| Actuator is already on the classpath | use the autoconfigured observability interceptor |

Server health and client health are separate concerns.
Server health publishes service state, while client health decides whether a channel should keep using an upstream endpoint.

Client health checks are optional; enable them only when the upstream exposes the gRPC health service and the deployment wants the channel to react to serving state.
If it does not, leave client health disabled and rely on explicit deadlines, retries, or transport-level failure handling.

### Observability baseline

When Spring Boot Actuator is already present, prefer the framework-provided gRPC observability integration over hand-rolled interceptors for metrics or tracing.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics
```

Keep custom interceptors for correlation ids, authorization, or request policy.
Do not duplicate observability behavior in a second interceptor chain unless the deployment has a concrete requirement that the default integration cannot satisfy.
