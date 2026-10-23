---
description: >-
  Open this when a Netty client or server must add TLS, trust configuration, or handshake-aware pipeline setup.
---

# TLS and SSL Pipeline Setup

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

## OPENSSL provider (production Linux)

Use the project's managed `netty-tcnative` version when present.
Otherwise, check `io.netty:netty-tcnative-boringssl-static` in Maven Central for the latest stable release compatible with the project's Netty version and target platform before setting `${netty.tcnative.version}`.

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-tcnative-boringssl-static</artifactId>
    <version>${netty.tcnative.version}</version>
    <classifier>linux-x86_64</classifier>
</dependency>
```

```java
SslContext sslContext = SslContextBuilder.forServer(new File("server.crt"), new File("server.key"))
    .sslProvider(SslProvider.OPENSSL)
    .protocols("TLSv1.3", "TLSv1.2")
    .build();
```

Use `SslProvider.OPENSSL` with `netty-tcnative` for higher throughput on Linux.

> [!NOTE]
>
> `netty-tcnative-boringssl-static` bundles BoringSSL statically.
> No external OpenSSL installation is required.
> For system OpenSSL, use the dynamically linked `io.netty:netty-tcnative` artifact with the matching platform classifier.

The system-OpenSSL artifact uses the project's managed `${netty.tcnative.version}` and a target platform classifier:

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-tcnative</artifactId>
    <version>${netty.tcnative.version}</version>
    <classifier>linux-x86_64</classifier>
</dependency>
```

Its published POM marks the native library as dynamically linked and provides the OpenSSL and APR metadata.
Install compatible OpenSSL and APR native libraries, and ensure the runtime loader can find them.

## Guidance

- keep self-signed or insecure trust managers out of normal production examples
- treat handshake failures as connection failures and surface them in `exceptionCaught`
- add protocol-specific framing and codecs after the TLS handler
- use `SslProvider.OPENSSL` (via netty-tcnative) on Linux for production throughput.
  - `SslProvider.JDK` is portable but slower under load
- prefer `TLSv1.3` as the first protocol in the protocols list.
  - Fall back to `TLSv1.2` for older clients
