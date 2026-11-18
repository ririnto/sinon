---
metadata:
  reference:
    Spring gRPC:
      version: 1.1.1
      url:
        - https://docs.spring.io/spring-grpc/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/grpc/spring-grpc-dependencies/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/grpc/spring-grpc-dependencies/1.1.1/spring-grpc-dependencies-1.1.1.pom
    Spring Boot:
      version: 4.1.1
      url:
        - https://docs.spring.io/spring-boot/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom
name: spring-grpc
description: >-
  Use Spring gRPC for protobuf services, channels, deadlines, interceptors, health checks, and reflection.
---

# Spring gRPC

The examples target Spring gRPC 1.1.x.
On this line, Spring Boot 4.1 provides auto-configuration for gRPC servers and clients, so the ordinary path in this skill uses the Boot-managed starter artifacts.

## Boundaries

Use `spring-grpc` for gRPC transport, generated protobuf stubs, Spring-managed gRPC services, channel configuration, request metadata, and gRPC-specific error handling.

- Use narrower guidance for HTTP, reactive HTTP, or GraphQL API design.
- Keep business logic outside the gRPC transport class.
  - Service implementations should translate between protobuf contracts and application services.

## Starter and runtime decisions

| Situation | Use |
| --- | --- |
| Application only serves gRPC | server starter |
| Application only calls another gRPC service | client starter |
| Same application serves and calls gRPC | both starters |
| Operators or local tooling need descriptor discovery | leave the server starter's reflection service enabled |

Treat reflection exposure as an explicit deployment decision even though the server starter includes the service and enables it by default.

## Dependency and generation baseline

Use only the server or client starters required by the application on a compatible Spring gRPC line.
For a new Boot BOM, check Maven Central for the latest compatible stable release; keep existing pins unless a change is authorized.
Keep Boot BOM-managed starter coordinates versionless.
The Boot server starter includes reflection and health support; add `io.grpc:grpc-services` explicitly only for a manual runtime without that starter.
Generate types from `.proto` files before implementing services and clients; place contracts under `src/main/proto`.
With Boot's starter parent, use its managed protobuf Maven plugin; otherwise configure the plugin directly and prefer Boot's managed protobuf and gRPC version properties.
Keep generated code in build output, not hand-maintained source directories; if the project tracks generated code, review it with the `.proto` contract.
Use explicit static addresses for local development and add discovery or advanced channel configuration only when deployment requires it.

For dependency declarations, generation setup, configuration examples, or first test commands, read [dependencies and configuration](references/dependencies-and-configuration.md).

## Client wiring

Import generated blocking stubs with `@ImportGrpcClients` by default.
Select specific types or a bean prefix when needed; use explicit beans with `GrpcChannelFactory` for custom channels or per-stub tuning.

## Coding procedure

1. Write the `.proto` file first and keep field numbers stable once the contract is published.
2. Generate stubs before implementing handlers so the server and client compile against the same contract.
3. Register shared transport concerns with `@GlobalServerInterceptor` and `@GlobalClientInterceptor`, then add per-service interceptors with `@GrpcService(interceptors = ..., blendWithGlobalInterceptors = true)` only when one service needs extra behavior.
4. Keep `@GrpcService` classes thin and delegate to application services for real work.
5. Translate validation failures and business failures into explicit gRPC statuses instead of leaking generic runtime exceptions.
6. Use `GrpcExceptionHandler` or `@GrpcExceptionHandler` when several handlers need the same status-mapping rule.
7. Attach metadata, deadlines, and interceptors at the client or server boundary, not inside core business services.
8. Enable reflection only when local debugging or tooling actually needs it.

## RPC and error boundaries

Use unary RPC and a blocking stub for ordinary request-response; select server, client, or bidirectional streaming for the matching message flow and future or async stubs when callers overlap remote calls.
Map invalid client input to `Status.INVALID_ARGUMENT`, and missing or denied access to the matching status.
Set deadlines for cross-service calls and attach correlation metadata at the transport boundary.
Centralize shared exception mapping and use per-service interceptors for service-specific transport policy.

## Health and observability

Server health publishes service state; client health gates calls to an upstream channel.
When Actuator is present, prefer its autoconfigured gRPC interceptor for metrics and tracing.
Use custom interceptors for correlation, authorization, or request policy.
For server or client health configuration and observability setup, read [health and observability](references/health-and-observability.md).

## Examples

For protobuf, server, client, interceptor, status, health, reflection, deadline, or test-port examples, read [implementation and output examples](references/implementation-and-output-examples.md).

## Testing checklist

Exercise the changed RPC contract through a generated client or server stub.

Verify `.proto` compilation, generated stubs and checked-in contract alignment, the expected server response, and the intended client channel.
Verify deadlines, metadata, interceptors, and invalid-input status on representative calls.
Use in-process transport or injected ports when a real network port is unnecessary, and verify reflection is disabled when descriptor exposure is unacceptable.

## Production checklist

- Keep published field numbers stable and never repurpose them after clients exist.
- Bound every cross-service call with an explicit deadline.
- Propagate correlation ids or tracing metadata consistently across client and server boundaries.
- Prefer the autoconfigured observability interceptor when Actuator is already in use instead of hand-rolling duplicate metrics logic.
- Distinguish server health publication from client health gating and enable each intentionally.
- Expose reflection only when operations tooling requires it.
- Use the Boot-managed gRPC starters on the 1.1.x line.
  Starter coordinates should align with the Boot version.
- Keep transport errors and application errors distinct so retries and observability stay meaningful.

## References

- Open [streaming and async stubs](references/streaming-and-async-stubs.md) when the ordinary blocking unary path is not enough and the task needs future-style stubs or streaming RPC patterns.
- Open [channel customization](references/channel-customization.md) when the deployment needs richer client-channel construction, global client interceptors, keepalive, retries, or per-channel tuning.
- Open [exception handling](references/exception-handling.md) when exception-to-status mapping needs reusable handler beans, `@GrpcExceptionHandler`, or different behavior per service.
- Open [in process testing](references/in-process-testing.md) when integration tests should use in-process transport, `@LocalGrpcServerPort`, or explicit test-only channel wiring.
- Open [security tls mtls](references/security-tls-mtls.md) when the deployment needs TLS or mTLS, Basic authentication, bearer tokens, or OAuth2 and server-authentication integration.
