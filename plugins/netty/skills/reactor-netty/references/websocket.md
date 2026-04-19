---
title: WebSocket
description: Open this when Reactor Netty must upgrade HTTP connections to WebSocket or exchange frames reactively.
---

## Open this when

Open this when the flow requires WebSocket upgrade, reactive frame exchange, or graceful close behavior.

## WebSocket server

```java
DisposableServer server = HttpServer.create()
    .port(8080)
    .route(routes -> routes.ws("/ws", (inbound, outbound) -> outbound.sendString(
        inbound.receive().asString().map(text -> "echo: " + text)
    )))
    .bindNow();
server.onDispose().block();
```

## WebSocket client

```java
HttpClient.create()
    .host("localhost")
    .port(8080)
    .websocket()
    .uri("/ws")
    .handle((inbound, outbound) -> outbound.sendString(Mono.just("hello"))
        .then()
        .thenMany(inbound.receive().asString().doOnNext(System.out::println))
        .then())
    .block();
```

## Graceful close

```java
HttpClient.create()
    .host("localhost")
    .port(8080)
    .websocket()
    .uri("/ws")
    .handle((inbound, outbound) -> inbound.receive().asString().flatMap(text -> {
        if ("close".equals(text)) {
            return outbound.sendClose(1000, "bye").then();
        }
        return outbound.sendString(Mono.just(text)).then();
    }).then())
    .block();
```

## Guidance

- treat WebSocket as an HTTP upgrade flow in Reactor Netty, not as low-level Netty pipeline work
- keep custom frame or pipeline manipulation out of this path unless the task has crossed into the `netty` skill domain
