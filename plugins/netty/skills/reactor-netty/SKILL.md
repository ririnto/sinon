---
metadata:
  reference:
    Reactor Netty:
      version: 1.3.7
      url:
        - https://projectreactor.io/docs/netty/1.3.7/reference/index.html
        - https://github.com/reactor/reactor-netty/releases/tag/v1.3.7
    Reactor Netty milestone:
      version: 1.4.0-M1
      url: https://github.com/reactor/reactor-netty/releases/tag/v1.4.0-M1
name: reactor-netty
description: >-
  Build or debug Reactor Netty HTTP, TCP, UDP, or QUIC clients and servers, including lifecycle and resource management.
---

# Reactor Netty

## Official Baseline

- Use the official Reactor Netty 1.3.x reference guide for this skill.
  The `v1.3.7` release and its 2025.0.7 Release Train are reviewed baselines, not dependency pins.
- Treat `v1.4.0-M1` as a milestone release, not a stable production version.
- Before adding or upgrading Reactor Netty, check `io.projectreactor:reactor-bom` and `io.projectreactor.netty:reactor-netty-http` in Maven Central for the latest stable compatible versions.
  Honor the project's existing platform, BOM, or pins, and keep Reactor Core and Reactor Netty on a compatible managed release train.
  Set `${reactor.bom.version}` to the selected release train only when the build does not manage Reactor dependencies.
- Confirm the target project's resolved Reactor Netty version before using version-sensitive APIs.
- Keep HTTP, TCP, UDP, and QUIC aligned with the Reactor Netty reference guide chapters for the same release line.

Complete the requested Reactor Netty change through its builder and reactive flow, preserving resource ownership and shutdown behavior.

## Goal

Keep the common path on Reactor Netty builders and reactive flow: `HttpServer`, `HttpClient`, `TcpServer`, `TcpClient`, `UdpServer`, `UdpClient`, `QuicServer`, and `QuicClient`.

- Treat `.handle((inbound, outbound) -> ...)` and HTTP route handlers as the main composition points.
- Do not block inside reactive handlers.
  - Use blocking only at process boundaries such as `bindNow()`, `connectNow()`, terminal response retrieval in top-level sample code, or `onDispose().block()`.
- Keep low-level Netty details out of the common path.
  - If the task needs `ChannelPipeline`, `ByteBuf.release()`, `ChannelFuture`, or custom codecs, use the lower-level Netty API model rather than forcing the builder surface.
- Dispose custom resources explicitly when you create them.

## When this skill fits

Use this skill for:

- reactive HTTP servers and clients
- reactive TCP, UDP, or QUIC servers and clients
- builder-based configuration with lifecycle callbacks such as `doOnBound`, `doOnConnected`, and `doOnConnection`
- resource-aware startup, warmup, and shutdown using Reactor Netty abstractions

Keep low-level Netty concerns out of this common path:

- `ServerBootstrap`, `Bootstrap`, `ChannelPipeline`, and handler ordering
- `ByteBuf` ownership and manual `release()`
- custom Netty codecs and frame decoders

## Task Context

Read the affected builder, handler, resource owner, and tests before changing transport or lifecycle behavior.
Reuse the project's dependency management and shared resources unless the task requires an authorized change.
Use the [reference table](#references) for resource, timeout, TLS, observability, or WebSocket details relevant to the task.
Examples do not authorize public listeners, external connections, deployed resource changes, or sensitive traffic capture.

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
Use `reactor-netty-quic` when the work includes QUIC.
That module provides `QuicServer` and `QuicClient`.

### Lifecycle and composition

- Server builders return `DisposableServer` from `bindNow()`.
- Client builders return `Connection` from `connectNow()`.
- HTTP routes return reactive send operations.
- TCP and UDP handlers compose inbound and outbound streams with `Publisher` chains.
- Use lifecycle hooks such as `doOnBind`, `doOnBound`, `doOnChannelInit`, `doOnConnection`, `doOnConnected`, and `doOnDisconnected` only when they change startup, channel extension, or shutdown behavior.

### Response consumption shapes

| Method | Use when | Return shape |
| --- | --- | --- |
| `.responseSingle((resp, content) -> ...)` | you need the status code + full body aggregated at once | `Mono<T>` - body is fully buffered |
| `.responseContent()` | you want streaming/chunked processing of the response body | `Flux<ByteBuf>` - each chunk arrives as it is received |

Aggregated body (status check + full body in one signal):

```java
String body = HttpClient.create()
    .get().uri("http://localhost:8080/hello")
    .responseSingle((resp, content) -> {
        if (resp.status().code() != 200) {
            return Mono.error(new RuntimeException("unexpected: " + resp.status()));
        }
        return content.asString();
    })
    .block();
```

Streaming body (process chunks as they arrive):

```java
List<String> chunks = HttpClient.create()
    .get().uri("http://localhost:8080/stream")
    .responseContent()
    .asString()
    .collectList()
    .block();
```

### Error handling in reactive handlers

Use `onErrorResume` when a handler must convert a reactive failure into an HTTP response, and let unexpected errors propagate when the caller should observe the failure:

Route-level fallback (compose the `500` response inside the handler):

```java
.route(routes -> routes
    .get("/hello", (req, res) -> res.sendString(Mono.just("Hello")))
    .get("/fail", (req, res) -> Mono.error(new RuntimeException("boom"))
        .onErrorResume(error -> res.status(500)
            .sendString(Mono.just("Error: " + req.uri()))
            .then())))
```

Handler-level recovery (map errors to fallback values):

```java
HttpClient.create()
    .get().uri("http://localhost:8080/hello")
    .responseSingle((resp, content) -> content.asString())
    .onErrorResume(TimeoutException.class,
        timeout -> Mono.just("fallback (timeout)"))
    .block();
```

Do not use blocking `try/catch` inside reactive lambdas.
Reactive errors travel through the error channel, not Java exceptions.

### Resource model

- Default loop resources are shared and usually sufficient for the common path.
- `warmup()` is optional but useful when startup latency matters.
- Use `.runOn(...)` only when the application truly needs custom loop resources.

### Operational entrypoints

- Use `.secure(...)` when the common path must switch to TLS.
- Use `.responseTimeout(...)` or channel options when a client must fail fast.
- Use wiretap only when traffic evidence is needed and the capture scope permits sensitive headers or payloads.
- Use `.metrics(true)` only when the application already has a metrics strategy.
- Open the blocker references when these concerns stop being one-line builder configuration.

## First safe commands

HTTP server:

```java
DisposableServer server = HttpServer.create()
    .port(8080)
    .route(routes -> routes
        .get("/hello", (request, response) -> response.sendString(Mono.just("Hello, World!")))
        .post("/echo", (request, response) -> response.status(201)
        .addHeader("X-Mode", "echo")
        .sendString(request.receive().asString().map(body -> "echo: " + body))))
    .bindNow();
server.onDispose().block();
```

HTTP client:

`ByteBufFlux.fromString` converts a `String` publisher into a `Flux<ByteBuf>` for request body sends.
`ByteBufFlux` is defined in Reactor Netty core and is available transitively when using `reactor-netty-http`.

```java
import reactor.netty.ByteBufFlux;

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

`warmup()` pre-initializes event loops without binding a port.
The `HttpServer` builder remains reusable - `bindNow()` can be called afterward.

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

`UdpServer.bindNow()` returns `Connection` (not `DisposableServer`).

```java
Connection udpServer = UdpServer.create()
    .port(9001)
    .handle((inbound, outbound) -> outbound.sendObject(inbound.receiveObject()))
    .bindNow();
udpServer.onDispose().block();
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

Lifecycle hook examples:

Server lifecycle (bind, bound, unbound):

```java
DisposableServer server = HttpServer.create()
    .port(8080)
    .doOnBind(config -> System.out.println("binding " + config.host() + ":" + config.port()))
    .doOnBound(bound -> System.out.println("bound " + bound.port()))
    .doOnUnbound(bound -> System.out.println("unbound " + bound.port()))
    .bindNow();
server.onDispose().block();
```

Channel init (access the low-level Netty `ChannelPipeline` before handlers run).
Use this when a channel-level option or handler must be set per-connection:

```java
HttpServer.create()
    .port(8080)
    .doOnChannelInit((observer, channel, remoteAddress) -> {
        channel.pipeline().addFirst(new TrafficLoggingHandler());
    })
    .bindNow();
```

## Decision points

| Decision | Default | Escalate when |
| --- | --- | --- |
| HTTP vs TCP vs UDP | choose the builder that matches the application protocol | the task needs low-level Netty framing or codecs |
| HTTP routing vs `.handle(...)` | use `.route(...)` for standard HTTP endpoints | use `.handle(...)` when you need lower-level response composition |
| `.responseSingle()` vs `.responseContent()` | `.responseSingle()` for status + aggregated body | use `.responseContent()` for streaming/chunked response processing |
| error handling strategy | propagate errors to the subscriber unless the handler must compose a fallback response with `onErrorResume` | per-handler recovery or explicit `500` response composition is needed for specific exception types |
| default resources vs custom resources | stay on defaults first | open [`event-loop-and-resources.md`](./references/event-loop-and-resources.md) for isolation or custom sizing |
| plain text vs TLS | start plain for local flow | open [`ssl-tls.md`](./references/ssl-tls.md) when certificates or HTTPS are required |
| simple lifecycle vs operational tuning | start with bind/connect + dispose | open [`timeouts-and-pool-tuning.md`](./references/timeouts-and-pool-tuning.md) or [`metrics-and-observability.md`](./references/metrics-and-observability.md) when production tuning appears |
| plain HTTP/TCP/UDP vs WebSocket | keep HTTP/TCP/UDP in the common path | open [`websocket.md`](./references/websocket.md) for WebSocket upgrade flow |

## Completion

Verify the affected behavior with the existing native tests, using real transport only when the contract requires it.
Check the relevant invariants below rather than testing every builder and protocol.

- [ ] selected builder matches the protocol being implemented
- [ ] bind/connect and disposal flow are explicit
- [ ] reactive handlers do not block internally
- [ ] lifecycle hooks are attached only where they affect behavior
- [ ] low-level Netty pipeline or buffer ownership details are not required for the common path
- [ ] error handling uses reactive operators such as `onErrorResume` or explicit response composition rather than blocking try/catch inside lambdas
- [ ] response consumption shape (`.responseSingle()` vs `.responseContent()`) matches the use case

## Common pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| blocking inside `.handle(...)`, route handlers, or response mapping | reactive execution stalls and hides latency under ordinary flow | keep blocking at process boundaries or isolate it behind a deliberate reactive bridge |
| dropping into `ChannelPipeline` customization for ordinary HTTP or TCP tasks | the solution leaves the builder-based common path and becomes harder to maintain | stay on Reactor Netty builders unless low-level Netty internals are the actual blocker |
| creating custom loop resources for every server or client by default | resource churn and disposal complexity rise without a clear benefit | stay on shared defaults first and open the resource reference only when isolation is required |
| adding lifecycle hooks everywhere | startup and connection flow become noisy without changing behavior | attach `doOn...` hooks only where they affect diagnostics, setup, or teardown |
| turning on wiretap or metrics as permanent defaults | noise or overhead grows in paths that do not need it | enable operational features deliberately for diagnostics or an existing observability strategy |
| using Reactor Netty when the real problem is codec or buffer ownership | the builder API stops being the main abstraction and guidance becomes misleading | address framing, codecs, buffer ownership, and other lower-level Netty APIs directly |

## References

Open these only when the common path is no longer enough:

| Blocker | Open |
| --- | --- |
| custom loop resources, shared providers, or explicit disposal ordering | [event-loop-and-resources.md](./references/event-loop-and-resources.md) |
| response timeout, connect timeout, retry, or connection pool tuning | [timeouts-and-pool-tuning.md](./references/timeouts-and-pool-tuning.md) |
| HTTPS, custom trust, or mTLS | [ssl-tls.md](./references/ssl-tls.md) |
| wiretap, metrics, or access logging | [metrics-and-observability.md](./references/metrics-and-observability.md) |
| WebSocket client or server flow | [websocket.md](./references/websocket.md) |

## Result

Complete the authorized change and explain its builder, response, resource, or shutdown consequences.
Report the checks run and any unverified transport or operational behavior.
