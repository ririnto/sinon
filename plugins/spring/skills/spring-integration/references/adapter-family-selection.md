# Spring Integration adapter family selection

Open this reference when choosing protocol adapters or module boundaries for a concrete external system.

## Representative adapter selection table

| Adapter family | Primary module | Use when |
| --- | --- | --- |
| File / FTP / SFTP | `spring-integration-file`, `spring-integration-ftp`, `spring-integration-sftp` | Filesystem or file-transfer boundaries drive the flow. |
| HTTP / WebFlux | `spring-integration-http`, `spring-integration-webflux` | The boundary is request-response HTTP or reactive HTTP. |
| JDBC / JPA | `spring-integration-jdbc`, `spring-integration-jpa` | Polling databases, metadata stores, or database-backed outbound work is required. |
| AMQP / Kafka | `spring-integration-amqp`, `spring-integration-kafka` | The Integration flow must bridge broker-backed channels or adapters. |

Treat this table as a representative starting point, not as the full Spring Integration adapter catalog.

## Selection rules

- Add only the adapter modules the flow actually uses.
- Keep configuration style consistent across the flow: Java DSL first for new code, annotations only when the surrounding codebase already standardized on them.
- Keep protocol-specific concerns at the adapter boundary instead of leaking them into transformers or routers.

## HTTP adapter shape

```java
IntegrationFlow.from(Http.inboundGateway("/orders"))
    .channel("orders.input")
```

## File inbound adapter shape

```java
IntegrationFlow.from(Files.inboundAdapter(new File("/inbox")),
        endpoint -> endpoint.poller(Pollers.fixedDelay(Duration.ofSeconds(5))))
    .channel("files.input")
```
