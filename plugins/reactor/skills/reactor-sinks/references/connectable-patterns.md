---
title: "Connectable Flux Patterns"
description: "Open this when the real problem is connect, disconnect, replay, or subscriber rendezvous for a shared cold source rather than manual sink emission."
---

Open this when a cold source already exists and the blocker is how multiple subscribers share, trigger, or replay it.

## Conceptual foundation

For the cold versus hot distinction and when sharing is the right design move, see [Hot, Cold, and Multicasting](../reactor-core/references/hot-cold-and-multicasting.md) in the `reactor-core` skill. This reference covers the concrete patterns after that conceptual decision is made.

## Choose the sharing pattern

| Need | Use | Why |
| --- | --- | --- |
| explicit start after subscribers are ready | `publish()` + `connect()` | manual connection point |
| start automatically after N subscribers | `autoConnect(n)` | subscriber rendezvous |
| stay connected while at least N subscribers remain | `refCount(n)` | automatic connect/disconnect |
| replay previous values to late subscribers | `replay(...)` | shared history for late subscribers |

## Manual `connect()`

```java
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;

final class ManualConnect {
    ConnectableFlux<Integer> source() {
        return Flux.range(1, 3).publish();
    }
}
```

## `autoConnect(...)`

```java
import reactor.core.publisher.Flux;

final class AutoConnectExample {
    Flux<Integer> source() {
        return Flux.range(1, 3)
            .publish()
            .autoConnect(2);
    }
}
```

## `refCount(...)`

```java
import reactor.core.publisher.Flux;

final class RefCountExample {
    Flux<Long> source() {
        return Flux.interval(java.time.Duration.ofSeconds(1))
            .publish()
            .refCount(1);
    }
}
```

## `replay(...)`

```java
import reactor.core.publisher.Flux;

final class ReplayExample {
    Flux<Integer> source() {
        return Flux.range(1, 5)
            .replay(2)
            .autoConnect(1);
    }
}
```

## Guardrails

- Use these patterns when you already have a cold source and want to share it.
- Do not replace a real manual producer with ConnectableFlux just because the stream should be hot.
- `refCount(...)` is for lifecycle coupling to subscriber count, not for retained history.
- `replay(...)` trades memory for late-subscriber history, so pick size or time limits deliberately.
