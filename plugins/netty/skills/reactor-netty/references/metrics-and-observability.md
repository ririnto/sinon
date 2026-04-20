---
title: Metrics and Observability
description: Open this when Reactor Netty needs wiretap logging, Micrometer metrics, HTTP access logging, or operational traffic inspection.
---

## Open this when

Open when you need to inspect traffic, expose metrics, add access logging, or gain operational visibility without changing the common request flow.

## Wiretap

Log all inbound and outbound bytes at `DEBUG` level. Use this for troubleshooting only — it generates significant volume.

```java
HttpClient client = HttpClient.create()
    .wiretap("reactor.netty.http.client.HttpClient", LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL);

DisposableServer server = HttpServer.create()
    .wiretap("reactor.netty.http.server.HttpServer", LogLevel.DEBUG, AdvancedByteBufFormat.TEXTUAL)
    .port(8080)
    .bindNow();
server.onDispose().block();
```

## Metrics (Micrometer)

Reactor Netty integrates with Micrometer. Add the Micrometer dependency (`io.micrometer:micrometer-core`) and bind a registry:

```java
DisposableServer server = HttpServer.create()
    .metrics(true, () -> new SimpleMeterRegistry())
    .port(8080)
    .bindNow();
server.onDispose().block();
```

Key metrics exposed:

| Metric name | Type | Description |
| --- | --- | --- |
| `reactor.netty.connection.total` | Counter | total number of connections |
| `reactor.netty.data.sent` | Distribution summary | bytes sent per connection |
| `reactor.netty.data.received` | Distribution summary | bytes received per connection |
| `errors` | Counter | total protocol errors |

Query a specific metric after traffic has flowed:

```java
MeterRegistry registry = ...;
double totalConnections = registry.find("reactor.netty.connection.total")
    .counter()
    .count();
```

## Access log

HTTP access logs follow a format similar to nginx:

```java
DisposableServer server = HttpServer.create()
    .accessLog(true)  // requires netty-http as a dependency
    .port(8080)
    .bindNow();
server.onDispose().block();
```

Access log output example:

```text
2026-04-20 10:15:00.000 GET /hello 200 12ms 127.0.0.1:54321
```

Fields: timestamp, method, path, status, duration, remote address.

## Guidance

- start with `wiretap(true)` for debugging and turn it down once the issue is understood
- enable `metrics(true)` where the application already has a Micrometer registry strategy; pass a shared registry rather than creating one per server
- keep observability features additive so the common path stays small
- wiretap and access log should not both be enabled in production — pick one visibility channel
