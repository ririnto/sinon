---
title: Event Loop and Resources
description: Open this when Reactor Netty needs custom LoopResources, shared ConnectionProvider instances, or explicit shutdown ordering.
---

## Open this when

Open this when shared default resources are no longer enough and the application needs isolated event loops, reusable pools, or controlled disposal order.

## Custom `LoopResources`

`LoopResources.create(prefix, workerCount, daemon)`:

| Parameter | Meaning |
| --- | --- |
| `prefix` | thread name prefix (e.g., `"app"` creates `app-nio-1`, `app-nio-2`, ...) |
| `workerCount` | number of event loop threads; defaults to available processors |
| `daemon` | whether event loop threads are daemon threads (`true` for server apps) |

```java
LoopResources loops = LoopResources.create("app", 4, true);

DisposableServer server = HttpServer.create()
    .runOn(loops)
    .port(8080)
    .bindNow();

server.onDispose().block();
loops.disposeLater().block();
```

## Shared `ConnectionProvider`

```java
ConnectionProvider provider = ConnectionProvider.builder("shared")
    .maxConnections(100)
    .pendingAcquireTimeout(Duration.ofSeconds(30))
    .build();

HttpClient clientA = HttpClient.create(provider);
HttpClient clientB = HttpClient.create(provider);
```

## Ordered shutdown

```java
LoopResources loops = LoopResources.create("app", 4, true);
ConnectionProvider provider = ConnectionProvider.builder("app").maxConnections(50).build();
DisposableServer server = HttpServer.create().runOn(loops).port(8080).bindNow();

server.disposeNow();
provider.disposeLater().block();
loops.disposeLater().block();
```

## Guidance

- keep custom resources close to the code that owns them
- dispose providers and loop resources explicitly when you created them
- avoid custom resources unless isolation or tuning is actually required
