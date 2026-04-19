---
name: reactor-netty
description: Build Reactor Netty HTTP, TCP, or UDP clients and servers with reactive request handling, lifecycle hooks, and resource-aware startup or shutdown. Use when the work is centered on `HttpServer`, `HttpClient`, `TcpServer`, `TcpClient`, `UdpServer`, or `UdpClient` rather than low-level Netty pipeline APIs.
metadata:
  title: Reactor Netty
  official_project_url: "https://projectreactor.io/docs/netty/release/reference/"
  reference_doc_urls:
    - "https://projectreactor.io/docs/netty/release/reference/"
    - "https://projectreactor.io/docs/netty/release/api/"
  version: "1.x"
---

# Reactor Netty

Build one Reactor Netty application path end to end: pick the transport, configure the builder, compose inbound and outbound flow, and shut resources down cleanly without dropping into low-level Netty internals.

## Operating rules

- Keep the common path on Reactor Netty builders and reactive flow: `HttpServer`, `HttpClient`, `TcpServer`, `TcpClient`, `UdpServer`, and `UdpClient`.
- Treat `.handle((inbound, outbound) -> ...)` and HTTP route handlers as the main composition points.
- Do not block inside reactive handlers. Use blocking only at process boundaries such as `bindNow()`, `connectNow()`, terminal response retrieval in top-level sample code, or `onDispose().block()`.
- Keep low-level Netty details out of the common path. If the task needs `ChannelPipeline`, `ByteBuf.release()`, `ChannelFuture`, or custom codecs, move the answer to those lower-level Netty APIs directly instead of the builder surface.
- Dispose custom resources explicitly when you create them.

## When this skill fits

Use this skill for:

- reactive HTTP servers and clients
- reactive TCP or UDP servers and clients
- builder-based configuration with lifecycle callbacks such as `doOnBound`, `doOnConnected`, and `doOnConnection`
- resource-aware startup, warmup, and shutdown using Reactor Netty abstractions

Keep low-level Netty concerns out of this common path:

- `ServerBootstrap`, `Bootstrap`, `ChannelPipeline`, and handler ordering
- `ByteBuf` ownership and manual `release()`
- custom Netty codecs and frame decoders

## Common-path workflow

1. Add the Reactor BOM and the module you actually need.
   - `reactor-netty-http` for HTTP and WebSocket work
   - `reactor-netty-core` for TCP or UDP work
2. Choose the builder that matches the transport.
   - `HttpServer` / `HttpClient`
   - `TcpServer` / `TcpClient`
   - `UdpServer` / `UdpClient`
3. Configure host, port, warmup needs, and the common handler entrypoint.
    - HTTP server: `.route(...)` or `.handle(...)`
    - HTTP client: request + body send/receive + status inspection
    - TCP or UDP: `.handle((inbound, outbound) -> ...)`
4. Bind or connect.
    - server: `bindNow()` returns `DisposableServer`
    - client: `connectNow()` returns `Connection`
5. Keep lifecycle hooks explicit when needed.
   - server: `doOnBind`, `doOnBound`, `doOnConnection`, `doOnUnbound`
   - client: `doOnConnect`, `doOnConnected`, `doOnDisconnected`
6. Shut down with `onDispose().block()` or explicit disposal.

## Core model

### Dependency entrypoint

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-bom</artifactId>
            <version>${reactor.bom.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.projectreactor.netty</groupId>
        <artifactId>reactor-netty-http</artifactId>
    </dependency>
</dependencies>
```

Use `reactor-netty-core` instead when the work is only TCP or UDP.

### Lifecycle and composition

- Server builders return `DisposableServer` from `bindNow()`.
- Client builders return `Connection` from `connectNow()`.
- HTTP routes return reactive send operations.
- TCP and UDP handlers compose inbound and outbound streams with `Publisher` chains.
- Use lifecycle hooks such as `doOnBind`, `doOnBound`, `doOnChannelInit`, `doOnConnection`, `doOnConnected`, and `doOnDisconnected` only when they change startup, channel extension, or shutdown behavior.

### Resource model

- Default loop resources are shared and usually sufficient for the common path.
- `warmup()` is optional but useful when startup latency matters.
- Use `.runOn(...)` only when the application truly needs custom loop resources.

### Operational entrypoints

- Use `.secure(...)` when the common path must switch to TLS.
- Use `.responseTimeout(...)` or channel options when a client must fail fast.
- Use `.wiretap(true)` first for traffic-level troubleshooting.
- Use `.metrics(true)` only when the application already has a metrics strategy.
- Open the blocker references when these concerns stop being one-line builder configuration.

## First safe commands

HTTP server:

```java
DisposableServer server = HttpServer.create()
    .port(8080)
    .route(routes -> routes.post("/echo", (request, response) -> response.status(201)
        .addHeader("X-Mode", "echo")
        .sendString(request.receive().asString().map(body -> "echo: " + body))))
    .bindNow();
server.onDispose().block();
```

HTTP client:

```java
String body = HttpClient.create()
    .post()
    .uri("http://localhost:8080/echo")
    .send(ByteBufFlux.fromString(Mono.just("hello")))
    .responseSingle((response, content) -> {
        if (response.status().code() != 201) {
            return Mono.error(new IllegalStateException("unexpected status: " + response.status()));
        }
        return content.asString().map(text -> response.responseHeaders().get("X-Mode") + ":" + text);
    })
    .block();
```

Warmup before first bind or connect:

```java
HttpServer server = HttpServer.create().port(8080);
server.warmup().block();
DisposableServer bound = server.bindNow();
bound.onDispose().block();
```

TCP server:

```java
DisposableServer server = TcpServer.create()
    .port(9000)
    .handle((inbound, outbound) -> outbound.sendString(inbound.receive().asString().map(text -> "echo: " + text)))
    .bindNow();
server.onDispose().block();
```

TCP client:

```java
Connection connection = TcpClient.create()
    .host("localhost")
    .port(9000)
    .handle((inbound, outbound) -> outbound.sendString(Mono.just("ping"))
        .then()
        .thenMany(inbound.receive().asString().doOnNext(System.out::println))
        .then())
    .connectNow();
connection.onDispose().block();
```

UDP server:

```java
Connection server = UdpServer.create()
    .port(9001)
    .handle((inbound, outbound) -> outbound.sendObject(inbound.receiveObject()))
    .bindNow();
server.onDispose().block();
```

UDP client:

```java
Connection connection = UdpClient.create()
    .host("localhost")
    .port(9001)
    .handle((inbound, outbound) -> outbound.sendString(Mono.just("ping"))
        .then()
        .thenMany(inbound.receive().asString().doOnNext(System.out::println))
        .then())
    .connectNow();
connection.onDispose().block();
```

Lifecycle hook example:

```java
DisposableServer server = HttpServer.create()
    .port(8080)
    .doOnBind(config -> logger.atInfo().log(() -> "binding " + config.host() + ":" + config.port()))
    .doOnBound(bound -> logger.atInfo().log(() -> "bound " + bound.port()))
    .doOnUnbound(bound -> logger.atInfo().log(() -> "unbound " + bound.port()))
    .bindNow();
server.onDispose().block();
```

## Decision points

| Decision | Default | Escalate when |
| --- | --- | --- |
| HTTP vs TCP vs UDP | choose the builder that matches the application protocol | the task needs low-level Netty framing or codecs |
| HTTP routing vs `.handle(...)` | use `.route(...)` for standard HTTP endpoints | use `.handle(...)` when you need lower-level response composition |
| default resources vs custom resources | stay on defaults first | open [`event-loop-and-resources.md`](./references/event-loop-and-resources.md) for isolation or custom sizing |
| plain text vs TLS | start plain for local flow | open [`ssl-tls.md`](./references/ssl-tls.md) when certificates or HTTPS are required |
| simple lifecycle vs operational tuning | start with bind/connect + dispose | open [`timeouts-and-pool-tuning.md`](./references/timeouts-and-pool-tuning.md) or [`metrics-and-observability.md`](./references/metrics-and-observability.md) when production tuning appears |
| plain HTTP/TCP/UDP vs WebSocket | keep HTTP/TCP/UDP in the common path | open [`websocket.md`](./references/websocket.md) for WebSocket upgrade flow |

## Validation checklist

- [ ] selected builder matches the protocol being implemented
- [ ] bind/connect and disposal flow are explicit
- [ ] reactive handlers do not block internally
- [ ] lifecycle hooks are attached only where they affect behavior
- [ ] low-level Netty pipeline or buffer ownership details are not required for the common path

## Common pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| blocking inside `.handle(...)`, route handlers, or response mapping | reactive execution stalls and hides latency under ordinary flow | keep blocking at process boundaries or isolate it behind a deliberate reactive bridge |
| dropping into `ChannelPipeline` customization for ordinary HTTP or TCP tasks | the solution leaves the builder-based common path and becomes harder to maintain | stay on Reactor Netty builders unless low-level Netty internals are the actual blocker |
| creating custom loop resources for every server or client by default | resource churn and disposal complexity rise without a clear benefit | stay on shared defaults first and open the resource reference only when isolation is required |
| adding lifecycle hooks everywhere | startup and connection flow become noisy without changing behavior | attach `doOn...` hooks only where they affect diagnostics, setup, or teardown |
| turning on wiretap or metrics as permanent defaults | noise or overhead grows in paths that do not need it | enable operational features deliberately for diagnostics or an existing observability strategy |
| using Reactor Netty when the real problem is codec or buffer ownership | the builder API stops being the main abstraction and guidance becomes misleading | move the answer to framing, codecs, buffer ownership, and other lower-level Netty APIs directly |

## Blocker references

Open these only when the common path is no longer enough:

| Blocker | Open |
| --- | --- |
| custom loop resources, shared providers, or explicit disposal ordering | [event-loop-and-resources.md](./references/event-loop-and-resources.md) |
| response timeout, connect timeout, retry, or connection pool tuning | [timeouts-and-pool-tuning.md](./references/timeouts-and-pool-tuning.md) |
| HTTPS, custom trust, or mTLS | [ssl-tls.md](./references/ssl-tls.md) |
| wiretap, metrics, or access logging | [metrics-and-observability.md](./references/metrics-and-observability.md) |
| WebSocket client or server flow | [websocket.md](./references/websocket.md) |

## Output contract

Return:

1. the requested Reactor Netty server, client, or handler code
2. the chosen builder and lifecycle hooks
3. the resource and shutdown reasoning
4. any blocker references still required
