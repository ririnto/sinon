# Spring Integration 7.1 flow design

## HTTP outbound: RestClient

`HttpRequestExecutingMessageHandler` now accepts a `RestClient`.
Prefer `RestClient` for new flows instead of `RestTemplate` configuration.

For http outbound: restclient, see [example 3](configuration-examples.md#example-3-http-outbound-restclient).

## Redis: DSL, GETDEL, native CAS/CAD

The Redis module provides a Java DSL via `org.springframework.integration.redis.dsl.Redis`.
`RedisMessageStore.doRemove` uses `GETDEL` (Redis 6.2+) instead of `GET`+`UNLINK` by default.
`RedisLockRegistry` uses native Redis CAS/CAD commands for lock renewal and release (Redis 8.4+) with automatic Lua fallback.

For redis: dsl, getdel, native cas/cad, see [example 4](configuration-examples.md#example-4-redis-dsl-getdel-native-cascad).

## JMS: header-to-property mapping and DSL JmsTemplate

JMS-backed message channels now map Spring Integration headers to JMS message properties and back.
The JMS DSL allows setting a custom `JmsTemplate` on outbound adapters.

## DSL-only mode

Set `spring.integration.annotations.enable=false` to skip messaging annotation post-processor registration (`MessagingAnnotationPostProcessor`, `MessagingAnnotationBeanPostProcessor`) in pure DSL scenarios.
This avoids unnecessary component scanning overhead when the flow uses only Java DSL without annotation-driven endpoints.

```properties
spring.integration.annotations.enable=false
```

## Transformer requiresReply

`MessageTransformingHandler.requiresReply` can no longer be modified.
An `UnsupportedOperationException` is thrown from `setRequiresReply()` to enforce that the transformer pattern cannot produce null replies.

## IntegrationPatternType: message_store

`MessageStore` now extends `IntegrationPattern` with a default `message_store` type.
This change makes message stores visible in the integration graph and monitoring systems.

## HTTP CORS alignment with Spring MVC

The HTTP and WebFlux inbound gateways now align `CrossOrigin` defaults with Spring MVC: `allowedOrigins` defaults to empty and `allowCredentials` defaults to `false`.
Use `originPatterns` for flexible origin matching instead of `allowCredentials`.

## CloudEvents support

The `spring-integration-cloudevents` module provides `ToCloudEventTransformer` and `FromCloudEventTransformer` for CloudEvents specification v1.0 interoperability.

## gRPC support

The `spring-integration-grpc` module provides `GrpcInboundGateway` and `GrpcOutboundGateway` for unary, server streaming, client streaming, and bidirectional streaming gRPC patterns.
