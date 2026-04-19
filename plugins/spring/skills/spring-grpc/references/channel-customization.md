# Spring gRPC channel customization

Open this reference when the deployment needs richer client-channel construction than the ordinary static-address path in `SKILL.md`, including keepalive, compression, retries, global client interceptors, or per-channel tuning.

## Managed channel customization

```java
@Bean
GrpcChannelBuilderCustomizer<?> greeterChannelCustomizer() {
    return (channelName, builder) -> {
        if ("greeter".equals(channelName)) {
            builder.useTransportSecurity();
        }
    };
}
```

Use Spring gRPC channel builder customizers so deployment-specific channel concerns stay in Spring configuration instead of being scattered across callers.

## Global client interceptors

```java
@Bean
@GlobalClientInterceptor
ClientInterceptor authInterceptor() {
    return MetadataUtils.newAttachHeadersInterceptor(new Metadata());
}
```

Use global client interceptors for metadata, tracing, or other cross-cutting client behavior that should apply to most client calls.

## Per-channel customization

```java
@Bean
GrpcChannelBuilderCustomizer<?> greeterOnly() {
    return (channelName, builder) -> {
        if ("greeter".equals(channelName)) {
            builder.enableRetry();
        }
    };
}
```

Keep global concerns global, and narrow customizers to a named channel when only one upstream needs the behavior.

## Filtering client interceptors

Use a `ClientInterceptorFilter` when one globally declared interceptor should not apply to every channel.

## Call credentials or metadata concerns

Keep metadata and other cross-cutting client concerns at the channel or interceptor boundary rather than inside application services.

## Channel customization choices

| Situation | Use |
| --- | --- |
| Static local endpoint | `static://` channel address |
| One interceptor should skip a channel | `ClientInterceptorFilter` |
| One upstream needs special tuning | named `GrpcChannelBuilderCustomizer` |
| TLS, mTLS, Basic auth, or bearer tokens | open the security reference |

## Transport tuning examples

Use channel customization when the deployment needs concerns such as:

- compression
- keepalive
- retry tuning
- interceptor scoping

## Decision points

| Situation | Use |
| --- | --- |
| Local development or fixed endpoint | static channel address |
| Repeated metadata on many calls | channel or client interceptor |
| Deployment-specific connection tuning | custom channel builder configuration |

Open [security-tls-mtls.md](security-tls-mtls.md) when channel customization turns into certificate, token, or OAuth2 design work.
