---
title: "Connectable Flux Patterns"
description: "Open this when the real problem is connect, disconnect, replay, or subscriber rendezvous for a shared cold source rather than manual sink emission."
---

Open this when a cold source already exists and the blocker is how multiple subscribers share, trigger, or replay it.

For the cold versus hot distinction and when sharing is the right design move, start with [Hot, Cold, and Multicasting](../../reactor-core/references/hot-cold-and-multicasting.md). This reference covers the concrete patterns after that conceptual decision is made.

## Choose the sharing pattern

| Need | Use | Why |
| --- | --- | --- |
| explicit start after subscribers are ready | `publish()` + `connect()` | manual connection point |
| start automatically after N subscribers | `autoConnect(n)` | subscriber rendezvous |
| stay connected while at least N subscribers remain | `refCount(n)` | automatic connect/disconnect |
| replay previous values to late subscribers | `replay(...)` | shared history for late subscribers |

## Manual `connect()`

Use `publish()` + `connect()` when you must control exactly when the source starts, independent of subscriber count. The source does not emit until `connect()` is called, regardless of how many subscribers have attached.

```java
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
final class ManualConnect {
    void demonstrate() {
        ConnectableFlux<Integer> source = Flux.range(1, 3).publish();
        source.subscribe(v -> System.out.println("sub1: " + v));
        source.connect();
    }
}
```

Output: `sub1: 1`, `sub1: 2`, `sub1: 3`. Without `connect()`, no signals flow even with an active subscription.

## `autoConnect(n)`: start automatically after N subscribers

The source starts only after N subscribers arrive. Prior subscribers are queued but do not trigger execution.

```java
import reactor.core.publisher.Flux;
final class AutoConnectExample {
    void demonstrate() {
        Flux<Integer> source = Flux.range(1, 3).publish().autoConnect(2);
        source.subscribe(v -> System.out.println("sub1: " + v));
        source.subscribe(v -> System.out.println("sub2: " + v));
    }
}
```

Output: both subscribers receive all values (`sub1: 1..3`, `sub2: 1..3`). The first subscriber alone does not trigger the source.

## `refCount(n)`: automatic connect and disconnect

`refCount(n)` connects when the Nth subscriber arrives **and disconnects (cancels the upstream)** when subscriber count drops below N. This is the primary reason to choose `refCount` over `autoConnect`.

```java
import reactor.core.publisher.Flux;
import java.time.Duration;
final class RefCountExample {
    void demonstrate() {
        Flux<Long> source = Flux.interval(Duration.ofMillis(200)).publish().refCount(1);
        var disposable1 = source.subscribe(v -> System.out.println("sub1: " + v));
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        var disposable2 = source.subscribe(v -> System.out.println("sub2: " + v));
        disposable1.dispose();
        disposable2.dispose();
    }
}
```

With threshold of 1, the source starts immediately on the first subscription. When `disposable1` disposes, the source stays alive because `disposable2` keeps the count at 1. When `disposable2` also disposes, the count drops below 1 and the upstream cancels.

## `replay(...)`: late subscribers see history

```java
import reactor.core.publisher.Flux;
final class ReplayExample {
    void demonstrate() {
        Flux<Integer> source = Flux.range(1, 5).replay(2).autoConnect(1);
        source.subscribe(v -> System.out.println("early: " + v));
        source.subscribe(v -> System.out.println("late: " + v));
    }
}
```

Early subscriber sees all 5 values. Late subscriber sees only the last 2 retained values (`4`, `5`) because `replay(2)` keeps a buffer of size 2.

## Guardrails

- Use these patterns when you already have a cold source and want to share it.
- Do not replace a real manual producer with ConnectableFlux just because the stream should be hot.
- `refCount(...)` is for lifecycle coupling to subscriber count, not for retained history.
- `replay(...)` trades memory for late-subscriber history, so pick size or time limits deliberately.
