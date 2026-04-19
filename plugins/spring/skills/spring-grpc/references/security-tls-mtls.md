# Spring gRPC security, TLS, and mTLS

Open this reference when the deployment needs encrypted transport or authentication for gRPC traffic, including server TLS, client mTLS, Basic authentication, bearer tokens, or Spring Security integration on either the gRPC server or client.

## Choose the security layer

| Situation | Use |
| --- | --- |
| Encrypt server traffic with existing Boot SSL material | `spring.grpc.server.ssl.bundle` |
| Upstream requires client certificates | mTLS channel configuration |
| Upstream expects Basic auth on each call | `BasicAuthenticationInterceptor` |
| Upstream expects bearer tokens | `BearerTokenAuthenticationInterceptor` |
| Server must authenticate incoming callers | Spring Security resource-server support |

## Server TLS shape

```yaml
spring:
  grpc:
    server:
      ssl:
        bundle: grpc-server
```

## Client TLS transport shape

```java
@Bean
GrpcChannelBuilderCustomizer<?> secureChannel() {
    return (channelName, builder) -> {
        if ("secure-greeter".equals(channelName)) {
            builder.useTransportSecurity();
        }
    };
}
```

Use this shape for client-side TLS transport. When the deployment requires mutual TLS, pair the secure channel with the client-certificate and trust material already chosen by the application runtime instead of treating `useTransportSecurity()` alone as a complete mTLS setup.

## Client Basic-auth shape

```java
@Bean
@GlobalClientInterceptor
ClientInterceptor basicAuthInterceptor() {
    return new BasicAuthenticationInterceptor("client", "secret");
}
```

## Client bearer-token shape

```java
@Bean
@GlobalClientInterceptor
ClientInterceptor bearerTokenInterceptor(OAuth2AuthorizedClientManager clients) {
    return new BearerTokenAuthenticationInterceptor(clients, "downstream");
}
```

## Server authentication direction

Use Spring Security resource-server or authentication configuration when the gRPC server must authenticate incoming callers. Keep authentication policy on the server boundary and pair it with explicit gRPC status mapping for denied or unauthenticated calls.

## Guardrails

- Keep certificates, tokens, and authentication interceptors at the transport boundary rather than inside business services.
- Treat server TLS and client credentials as separate decisions; many deployments need one without the other.
- Pair security wiring with explicit deadlines and health behavior so failed authentication does not look like a generic transport outage.
- Open [channel-customization.md](channel-customization.md) only when the blocker is channel construction or interceptor scoping rather than security policy itself.
