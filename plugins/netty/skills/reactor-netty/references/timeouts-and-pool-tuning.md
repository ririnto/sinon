---
title: Timeouts and Pool Tuning
description: Open this when Reactor Netty needs connect or response timeouts, retries, or connection pool tuning.
---

## Open this when

Open this when a client or server needs bounded waiting time, retry behavior, or tuned connection reuse under load.

## Response timeout

```java
HttpClient client = HttpClient.create()
    .responseTimeout(Duration.ofSeconds(10));
```

## Connect timeout

```java
TcpClient client = TcpClient.create()
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000);
```

## Tuned connection pool

```java
ConnectionProvider provider = ConnectionProvider.builder("http")
    .maxConnections(100)
    .maxIdleTime(Duration.ofSeconds(30))
    .maxLifeTime(Duration.ofMinutes(5))
    .pendingAcquireTimeout(Duration.ofSeconds(30))
    .evictInBackground(Duration.ofMinutes(1))
    .build();

HttpClient client = HttpClient.create(provider);
```

## Retry with backoff

```java
Mono<String> body = HttpClient.create()
    .get()
    .uri("http://localhost:8080/hello")
    .responseContent()
    .asString()
    .retryWhen(Retry.backoff(3, Duration.ofMillis(100)).maxBackoff(Duration.ofSeconds(2)));
```

## Guidance

- tune the pool only when reuse and concurrency are actual bottlenecks
- keep retry policy at the reactive boundary, not inside low-level handlers
- use timeouts to fail fast, not to hide slow downstream systems
