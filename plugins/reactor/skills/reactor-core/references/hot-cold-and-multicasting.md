---
title: "Hot, Cold, and Multicasting"
description: "Open this when shared subscriptions, replay, or late-subscriber behavior changes the design and you need to reason about cold versus hot Reactor sources."
---

Open this when identical subscriptions must not repeat the same work or when late subscribers must see shared or replayed signals.

Keep this reference at the boundary-recognition level. If the real work is choosing concrete `publish()`, `replay()`, `autoConnect(...)`, or `refCount(...)` patterns for a shared cold source, treat that as concrete connectable-source design rather than conceptual hot/cold classification.

## Core distinctions

| Need | Use | Notes |
| --- | --- | --- |
| re-run the source per subscriber | cold source | ordinary Reactor default |
| share one live subscription | `share()` or `publish().refCount(...)` | subscribers join an active source |
| delay connection until enough subscribers arrive | `autoConnect(n)` | useful for coordinated consumers |
| replay earlier signals to late subscribers | `replay(...)` | trades memory for history |

## Cold source: each subscriber re-runs work

```java
import reactor.core.publisher.Flux;
final class ColdSourceExample {
    Flux<Integer> coldSource() {
        return Flux.create(sink -> {
            System.out.println("source created for subscriber");
            sink.next(1);
            sink.next(2);
            sink.complete();
        });
    }
    void demonstrate() {
        Flux<Integer> source = coldSource();
        source.subscribe(v -> System.out.println("sub1: " + v));
        source.subscribe(v -> System.out.println("sub2: " + v));
    }
}
```

Each subscription triggers the `Flux.create` lambda again. Output:

```text
source created for subscriber
sub1: 1
sub1: 2
source created for subscriber
sub2: 1
sub2: 2
```

## Hot source with `share()`: single execution, multiple subscribers

```java
import reactor.core.publisher.Flux;
final class HotShareExample {
    Flux<Integer> hotSource() {
        return Flux.create(sink -> {
            System.out.println("source created once");
            sink.next(1);
            sink.next(2);
            sink.complete();
        }).share();
    }
    void demonstrate() {
        Flux<Integer> source = hotSource();
        source.subscribe(v -> System.out.println("sub1: " + v));
        source.subscribe(v -> System.out.println("sub2: " + v));
    }
}
```

The `share()` operator converts the cold source into a hot one. The second subscriber joins the already-active source and may miss earlier signals if they were emitted before the second subscription arrived.

Output:

```text
source created once
sub1: 1
sub1: 2
sub2:   (may see nothing if source completed before sub2 arrived)
```

## `autoConnect(n)`: delay until N subscribers

```java
import reactor.core.publisher.Flux;
import java.time.Duration;
final class AutoConnectExample {
    Flux<Integer> coordinatedSource() {
        return Flux.interval(Duration.ofMillis(100))
            .publish()
            .autoConnect(2);
    }
}
```

Use `autoConnect(n)` when the source is expensive and should not run until enough consumers are ready. The source starts only after N subscribers arrive.

## Design checks

- `Flux.just(...)` captures values eagerly at assembly time, but each subscriber still receives the stored values.
- `defer(...)` keeps source creation cold and subscription-specific.
- `share()` is the shortest move when one live subscription is enough and replay is not needed.
- If the design requires manual emission semantics rather than shared subscription semantics, treat it as a sink problem.
- If you need concrete ConnectableFlux lifecycle recipes, shift from boundary recognition to connectable-source lifecycle design.

## Failure checks

- If subscribers must each re-run the source, keep it cold.
- If backpressure behavior changes after `publish()` or `share()`, inspect shared downstream demand rather than only the original source.
- If you need explicit manual emission APIs or replay buffers by policy, handle that as a separate hot-source design problem.
