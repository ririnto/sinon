# Transport and Lifecycle Examples

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
