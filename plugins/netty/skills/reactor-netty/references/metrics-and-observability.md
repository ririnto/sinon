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

Reactor Netty integrates with Micrometer through `io.micrometer:micrometer-core`. The built-in HTTP metrics overloads register meters in Micrometer's global registry and accept URI and method tag mappers. Keep URI tags templated or bounded to avoid high-cardinality meters.

```java
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.config.MeterFilter;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

Metrics.globalRegistry.config().meterFilter(
    MeterFilter.maximumAllowableTags("reactor.netty.http.server", "uri", 100, MeterFilter.deny()));

DisposableServer server = HttpServer.create()
    .metrics(true, uri -> uri.startsWith("/users/") ? "/users/{id}" : uri)
    .port(8080)
    .route(routes -> routes.get("/users/{id}", (request, response) -> response.sendString(
        request.receive().thenReturn("ok"))))
    .bindNow();
server.onDispose().block();
```

Use a custom `ChannelMetricsRecorder` only when you need to redirect low-level channel metrics to your own recorder instead of using the built-in Micrometer recorder:

```java
import java.net.SocketAddress;
import java.time.Duration;
import reactor.netty.DisposableServer;
import reactor.netty.channel.ChannelMetricsRecorder;
import reactor.netty.http.server.HttpServer;

final class LoggingChannelMetricsRecorder implements ChannelMetricsRecorder {
    @Override
    public void recordDataReceived(SocketAddress remoteAddress, long bytes) {
        System.out.println("received " + bytes + " from " + remoteAddress);
    }

    @Override
    public void recordDataSent(SocketAddress remoteAddress, long bytes) {
        System.out.println("sent " + bytes + " to " + remoteAddress);
    }

    @Override
    public void incrementErrorsCount(SocketAddress remoteAddress) {
        System.out.println("error from " + remoteAddress);
    }

    @Override
    public void recordTlsHandshakeTime(SocketAddress remoteAddress, Duration time, String status) {
        System.out.println("tls " + status + " from " + remoteAddress + " in " + time);
    }

    @Override
    public void recordConnectTime(SocketAddress remoteAddress, Duration time, String status) {
        System.out.println("connect " + status + " to " + remoteAddress + " in " + time);
    }

    @Override
    public void recordResolveAddressTime(SocketAddress remoteAddress, Duration time, String status) {
        System.out.println("resolve " + status + " for " + remoteAddress + " in " + time);
    }
}

DisposableServer server = HttpServer.create()
    .metrics(true, LoggingChannelMetricsRecorder::new)
    .port(8080)
    .bindNow();
server.onDispose().block();
```

Representative HTTP server metrics exposed by the built-in Micrometer path:

| Metric name | Type | Description |
| --- | --- | --- |
| `reactor.netty.http.server.connections.active` | Gauge | HTTP connections currently processing requests |
| `reactor.netty.http.server.data.sent` | Distribution summary | bytes sent |
| `reactor.netty.http.server.data.received` | Distribution summary | bytes received |
| `reactor.netty.http.server.errors` | Counter | protocol errors |
| `reactor.netty.http.server.response.time` | Timer | request/response time |
| `reactor.netty.bytebuf.allocator.used.direct.memory` | Gauge | direct memory reserved by the allocator |
| `reactor.netty.eventloop.pending.tasks` | Gauge | pending scheduled event loop tasks |

Query a specific metric after traffic has flowed:

```java
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

MeterRegistry registry = ...;
Gauge activeGauge = registry.find("reactor.netty.http.server.connections.active").gauge();
Double activeConnections = activeGauge != null ? activeGauge.value() : null;
```

## Access log

HTTP access logs are available on `HttpServer` from the `reactor-netty-http` module. Enable them programmatically with `.accessLog(true)` or by setting `-Dreactor.netty.http.server.accessLogEnabled=true`.

```java
DisposableServer server = HttpServer.create()
    .accessLog(true)
    .port(8080)
    .bindNow();
server.onDispose().block();
```

Access logs use Common Log Format by default. Configure the `reactor.netty.http.server.AccessLog` logger in Logback or another SLF4J backend when the access log needs a separate destination.

Logback access logger:

```xml
<appender name="accessLog" class="ch.qos.logback.core.FileAppender">
    <file>access_log.log</file>
    <encoder>
        <pattern>%msg%n</pattern>
    </encoder>
</appender>
<logger name="reactor.netty.http.server.AccessLog" level="INFO" additivity="false">
    <appender-ref ref="accessLog" />
</logger>
```

Access log output shape:

```text
127.0.0.1 - - [20/Apr/2026:10:15:00 +0000] "GET /hello HTTP/1.1" 200 12 15
```

Fields include remote host, identity placeholders, timestamp, request line, status, response bytes, and duration.

## Guidance

- start with `wiretap(true)` for debugging and turn it down once the issue is understood
- enable `metrics(true)` where the application already has a Micrometer registry strategy; pass a shared registry rather than creating one per server
- keep observability features additive so the common path stays small
- wiretap and access log should not both be enabled in production — pick one visibility channel
