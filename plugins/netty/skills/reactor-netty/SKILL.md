---
name: reactor-netty
description: >-
  Use this skill when the user asks to "create a Reactor Netty HTTP server",
  "build a reactive TCP client", "configure Reactor Netty connection pooling",
  "use HttpClient or HttpServer", "set up SSE with Reactor Netty",
  "configure UDP with Reactor Netty", or needs guidance on reactive network
  programming with Project Reactor and Netty.
metadata:
  title: "Reactor Netty"
  official_project_url: "https://projectreactor.io/docs/netty/release/reference/index.html"
  reference_doc_urls:
    - "https://projectreactor.io/docs/netty/release/reference/index.html"
    - "https://projectreactor.io/docs/core/release/reference/index.html"
  version: "1.2"
---

# Reactor Netty

## Overview

Reactor Netty is a non-blocking, reactive HTTP, TCP, and UDP client/server library built on Project Reactor and Netty. It provides a fully reactive programming model with Mono and Flux operators for building high-performance, scalable network applications with minimal resource consumption.

## Use This Skill When

- User asks to "create a Reactor Netty HTTP server"
- User asks to "build a reactive TCP client"
- User asks to "configure Reactor Netty connection pooling"
- User asks to "use HttpClient or HttpServer"
- User asks to "set up SSE with Reactor Netty"
- User asks to "configure UDP with Reactor Netty"
- User needs guidance on reactive network programming with Project Reactor and Netty
- User asks about routing, compression, access logs, or static files in reactive HTTP
- User needs help with HTTP client features like redirects, compression, or response handling

## Common-Case Workflow

1. Add dependencies to your project (Maven/Gradle)
2. Create a HttpServer builder or HttpClient for your application
3. Configure routes, handlers, and middleware
4. Apply compression, logging, and static file serving if needed
5. Start the server or make client requests
6. Handle request/response streams with Mono/Flux
7. Dispose resources on shutdown

## Reactor Netty Decision Path

| When to use... | Choose... |
| --- | --- |
| HTTP server with routing, compression, static files | `HttpServer` with route handlers |
| HTTP client with request/response, redirects, compression | `HttpClient` with request/response handling |
| TCP server for custom protocols, binary data | `TcpServer` with channel handlers |
| TCP client for custom protocol communication | `TcpClient` with channel handlers |
| UDP server for datagram communication | `UdpServer` for message-based communication |
| UDP client for broadcasting or datagram sending | `UdpClient` for message sending |
| Server-sent events (SSE) | `HttpServer` with `response.send()` and `text/event-stream` |
| High-performance connection pooling | `ConnectionProvider` with `HttpClient` |
| Mono vs Flux | Mono: single value (response, connection); Flux: stream of data (messages, chunks) |
| Reactor Netty vs core Netty | Reactor Netty: reactive model with Mono/Flux; Core Netty: low-level channel handling |

## Ready-to-Adapt Templates

### HTTP Server with Routes

```java
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

public class ReactiveHttpServer {

    public static void main(String[] args) {
        DisposableServer server = HttpServer.create()
            .port(8080)
            .route(routes -> routes
                .get("/hello", (request, response) -> 
                    response.sendString(Mono.just("Hello, Reactor Netty!")))
                .post("/api/data", (request, response) -> 
                    response.send(request.receive().asString().map(data -> 
                        "Received: " + data)))
                .get("/error", (request, response) -> 
                    response.status(500).sendString(Mono.just("Error occurred")))
                .get("/static/**", (request, response) -> 
                    response.sendFile("path/to/static/file.html"))
            )
            .bindNow();

        server.onDispose().block();
    }
}
```

### HTTP Client

```java
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

public class ReactiveHttpClient {

    public static void main(String[] args) {
        // GET request
        Mono<String> getResponse = HttpClient.create()
            .get()
            .uri("https://example.com/api/data")
            .responseSingle((response, body) -> 
                response.status().code().flatMap(statusCode -> {
                    if (statusCode == 200) {
                        return body.asString();
                    } else {
                        return Mono.error(new RuntimeException("HTTP " + statusCode));
                    }
                }))
            .doOnSuccess(System.out::println)
            .doOnError(System.err::println)
            .block();

        // POST request
        Mono<String> postResponse = HttpClient.create()
            .post()
            .uri("https://example.com/api/create")
            .sendString(Mono.just("{\"name\":\"John\",\"age\":30}"))
            .responseSingle((response, body) -> 
                response.status().code().flatMap(statusCode -> {
                    if (statusCode == 200) {
                        return body.asString();
                    } else {
                        return Mono.error(new RuntimeException("HTTP " + statusCode));
                    }
                }))
            .doOnSuccess(System.out::println)
            .doOnError(System.err::println)
            .block();
    }
}
```

### TCP Server

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;
import reactor.netty.tcp.TcpServerConfig;
import reactor.netty.tcp.TcpInbound;
import reactor.netty.tcp.TcpOutbound;

public class ReactiveTcpServer {

    public static void main(String[] args) {
        DisposableServer server = TcpServer.create()
            .port(9090)
            .handle((inbound, outbound) -> 
                inbound.receive()
                    .asString()
                    .flatMap(data -> {
                        System.out.println("Received: " + data);
                        return outbound.sendString(Mono.just("Echo: " + data))
                            .then(Mono.just(data));
                    })
                    .takeUntil(data -> data.equalsIgnoreCase("quit"))
                    .count())
            .bindNow();

        server.onDispose().block();
    }
}
```

### UDP Server

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.udp.UdpServer;
import reactor.netty.udp.UdpInbound;
import reactor.netty.udp.UdpOutbound;

public class ReactiveUdpServer {

    public static void main(String[] args) {
        DisposableServer server = UdpServer.create()
            .port(9999)
            .handle((inbound, outbound) -> 
                inbound.receive()
                    .asString()
                    .flatMap(data -> {
                        System.out.println("Received UDP message: " + data);
                        return outbound.sendString(Mono.just("UDP Ack: " + data))
                            .then(Mono.just(data));
                    })
                    .takeUntil(data -> data.equalsIgnoreCase("shutdown"))
                    .count())
            .bindNow();

        server.onDispose().block();
    }
}
```

### SSE Server Endpoint

```java
import reactor.core.publisher.Flux;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import java.time.Duration;

public class SseServer {

    public static void main(String[] args) {
        DisposableServer server = HttpServer.create()
            .port(8080)
            .route(routes -> routes
                .get("/events", (request, response) -> 
                    response.header("Content-Type", "text/event-stream")
                        .sendString(
                            Flux.interval(Duration.ofSeconds(1))
                                .map(sequence -> 
                                    "data: Message " + sequence + "\n\n")
                                .take(10)
                        )
                )
            )
            .bindNow();

        server.onDispose().block();
    }
}
```

### Connection Pool Configuration

```java
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.http.client.HttpClientResponse;

public class HttpClientWithConnectionPool {

    public static void main(String[] args) {
        ConnectionProvider provider = ConnectionProvider.builder("my-pool")
            .maxConnections(100)            // Maximum connections in pool
            .pendingAcquireTimeout(Duration.ofSeconds(30))  // Timeout for acquiring connection
            .maxIdleTime(Duration.ofSeconds(60))           // Max time connection can be idle
            .build();

        HttpClient client = HttpClient.create(provider);

        // Use the client with connection pooling
        Mono<String> response = client.get()
            .uri("https://api.example.com/data")
            .responseSingle((response, body) -> 
                response.status().code()
                    .flatMap(status -> status == 200 ? 
                        body.asString() : Mono.error(new RuntimeException("HTTP " + status)))
            )
            .doOnSuccess(System.out::println)
            .doOnError(System.err::println)
            .block();

        // Dispose the connection provider when done
        provider.dispose();
    }
}
```

## Validate the Result

- [ ] Server starts without errors and binds to specified port
- [ ] Routes are correctly mapped and respond to requests
- [ ] Request/response streams handle backpressure properly
- [ ] Connection pool sizes and timeouts are configured correctly
- [ ] Error handling catches exceptions and returns appropriate HTTP status codes
- [ ] Resources are properly disposed with `onDispose().block()`
- [ ] Mono/Flux chains are subscribed for operations to execute
- [ ] No blocking calls are made inside reactor handlers
- [ ] Proper HTTP headers (Content-Type, Content-Encoding) are set
- [ ] Client handles redirects and timeouts appropriately

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| WebSocket client or server with Reactor Netty | [`./references/websocket.md`](./references/websocket.md) |
| event loop group customization, resource lifecycle, or shared ConnectionProvider | [`./references/event-loop-and-resources.md`](./references/event-loop-and-resources.md) |
| SSL/TLS configuration for HTTP or TCP | [`./references/ssl-tls.md`](./references/ssl-tls.md) |
| timeout configuration, retry strategies, or connection pool tuning | [`./references/timeouts-and-pool-tuning.md`](./references/timeouts-and-pool-tuning.md) |
| metrics, observability, wire logging, or Micrometer integration | [`./references/metrics-and-observability.md`](./references/metrics-and-observability.md) |

## Invariants

- MUST subscribe to returned Mono/Flux for the operation to execute
- MUST NOT block inside Reactor Netty handlers (Mono/Flux pipelines)
- SHOULD use `ConnectionProvider` for HTTP client connection pooling
- MUST dispose server/client resources on shutdown using `onDispose().block()`
- MUST handle backpressure in reactive streams to prevent memory issues
- SHOULD use proper error handling with `onErrorResume()` or `onErrorReturn()`
- MUST set appropriate Content-Type headers for HTTP responses
- SHOULD use `response.status()` for custom HTTP status codes
- MUST handle null checks and proper input validation in handlers
- SHOULD configure appropriate timeouts for connections and operations

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| Blocking calls in handlers | Blocks event loop, defeats reactive benefits | Use `Schedulers.boundedElastic()` for blocking operations |
| Not subscribing to Mono/Flux | Operations never execute, no side effects | Always call `.block()`, `.subscribe()`, or reactive operators |
| Forgetting resource disposal | Connection leaks, resource exhaustion | Use `onDispose().block()` or `Disposable.dispose()` |
| Ignoring backpressure | Memory exhaustion under high load | Use `onBackpressureBuffer()`, `onBackpressureDrop()` |
| Using `block()` inside handlers | Blocks reactive pipeline, poor performance | Keep pipeline reactive or use scheduler for blocking |
| Not handling error cases | Unhandled exceptions crash the server | Add error handling with `onErrorResume()` or `onErrorReturn()` |
| Hardcoding configuration | No flexibility, difficult to test | Use external configuration with environment variables |
| No connection pooling | Poor performance, resource waste | Use `ConnectionProvider` for HTTP clients |
| Missing Content-Type headers | Browser parsing issues, wrong behavior | Always set appropriate `Content-Type` headers |
| Not handling redirects | Client stuck on same URL, infinite loops | Handle redirects with `followRedirect(true)` for clients |

## Scope Boundaries

Activate this skill for:
- Creating HTTP servers with routing, compression, static files, and SSE
- Building HTTP clients with request/response handling, redirects, and compression
- Setting up TCP client/server for custom protocols
- Configuring UDP client/server for datagram communication
- Managing connection pooling and lifecycle
- Understanding reactive programming patterns with Mono/Flux
- Performance tuning and resource management

Do not use this skill as the primary source for:
- Low-level Netty ChannelHandlerContext and ByteBuf manipulation
- Spring Boot configuration (use Spring Boot skills instead)
- Traditional blocking HTTP server frameworks (Tomcat, Jetty)
- Database connectivity (use JDBC or R2DBC skills)
- Security configuration (Spring Security, OAuth2)
- Load balancing and reverse proxy configuration (HAProxy, Nginx)
- Kubernetes networking and service mesh configuration