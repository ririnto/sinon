---
title: SSL and TLS
description: Open this when Reactor Netty must serve HTTPS, trust custom certificates, or use mutual TLS.
---

## Open this when

Open this when the application must switch from plain HTTP or TCP to secure transport with certificates or client authentication.

## HTTPS server

```java
SelfSignedCertificate certificate = new SelfSignedCertificate();
SslContext sslContext = SslContextBuilder.forServer(certificate.certificate(), certificate.privateKey()).build();

DisposableServer server = HttpServer.create()
    .secure(spec -> spec.sslContext(sslContext))
    .port(8443)
    .bindNow();
server.onDispose().block();
```

## HTTPS client

```java
SslContext sslContext = SslContextBuilder.forClient()
    .trustManager(new File("server.crt"))
    .build();

String body = HttpClient.create()
    .secure(spec -> spec.sslContext(sslContext))
    .get()
    .uri("https://localhost:8443/hello")
    .responseContent()
    .asString()
    .block();
```

## Mutual TLS

```java
SslContext serverContext = SslContextBuilder.forServer(new File("server.crt"), new File("server.key"))
    .trustManager(new File("client.crt"))
    .build();

DisposableServer server = HttpServer.create()
    .secure(spec -> spec.sslContext(serverContext).handlerConfigurator(handler -> handler.engine().setNeedClientAuth(true)))
    .port(8443)
    .bindNow();
server.onDispose().block();
```

## Guidance

- keep insecure trust managers out of normal examples
- use `.secure(...)` as the Reactor Netty entrypoint rather than low-level pipeline wiring
- keep certificate and trust material management outside the common path until TLS is actually needed
