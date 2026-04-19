---
title: Metrics and Observability
description: Open this when Reactor Netty needs wiretap logging, metrics, or HTTP access logging.
---

## Open this when

Open this when you need to inspect traffic, expose metrics, or add operational visibility without changing the common request flow.

## Wiretap

```java
HttpClient client = HttpClient.create()
    .wiretap(true);

DisposableServer server = HttpServer.create()
    .wiretap(true)
    .port(8080)
    .bindNow();
server.onDispose().block();
```

## Metrics

```java
DisposableServer server = HttpServer.create()
    .metrics(true)
    .port(8080)
    .bindNow();
server.onDispose().block();
```

## Access log

```java
DisposableServer server = HttpServer.create()
    .accessLog(true)
    .port(8080)
    .bindNow();
server.onDispose().block();
```

## Guidance

- start with `wiretap(true)` for debugging and turn it down once the issue is understood
- enable metrics where the application already has a registry strategy
- keep observability features additive so the common path stays small
