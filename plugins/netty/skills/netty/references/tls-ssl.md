---
title: TLS and SSL Pipeline Setup
description: Open this when a Netty client or server must add TLS, trust configuration, or handshake-aware pipeline setup.
---

## Open this when

Open this when a channel must be secured with TLS, a client must trust a certificate chain, or handshake order affects the pipeline.

## Server context

```java
SslContext sslContext = SslContextBuilder.forServer(certificateFile, privateKeyFile)
    .sslProvider(SslProvider.JDK)
    .protocols("TLSv1.2", "TLSv1.3")
    .build();
```

## Client context

```java
SslContext sslContext = SslContextBuilder.forClient()
    .trustManager(certificateFile)
    .sslProvider(SslProvider.JDK)
    .protocols("TLSv1.2", "TLSv1.3")
    .build();
```

## Pipeline placement

```java
final class SecureInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
            .addLast(sslContext.newHandler(ch.alloc()))
            .addLast(new BusinessHandler());
    }
}
```

Add TLS before application handlers so downstream handlers receive decrypted traffic.

## Guidance

- keep self-signed or insecure trust managers out of normal production examples
- treat handshake failures as connection failures and surface them in `exceptionCaught`
- add protocol-specific framing and codecs after the TLS handler
