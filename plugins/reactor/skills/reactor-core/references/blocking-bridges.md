---
title: "Advanced Blocking Bridges"
description: "Open this when one blocking boundary is not enough and you need multiple blocking boundaries, fromRunnable/fromFuture bridging nuances, block() terminal edges, or virtual-thread considerations."
---

Open this when a single `Mono.fromCallable(...)` + `subscribeOn(boundedElastic())` bridge is no longer sufficient and the pipeline requires multi-boundary blocking integration, future-based handoff patterns, or terminal bridge semantics.

For one ordinary blocking boundary, keep the default `Mono.fromCallable(...)` plus `subscribeOn(Schedulers.boundedElastic())` pattern from `SKILL.md`; this reference covers deeper bridge variants only.

## Multiple blocking boundaries

When two or more independent blocking calls must each run on separate bounded-elastic workers, wrap each call individually rather than scattering blocking work across operators.

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
final class MultiBoundaryPipeline {
    Flux<String> process(String userId) {
        return Mono.fromCallable(() -> fetchProfile(userId))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(profile -> Mono.fromCallable(() -> enrichProfile(profile))
                .subscribeOn(Schedulers.boundedElastic()))
            .map(EnrichedProfile::format)
            .flux();
    }
    private Profile fetchProfile(String userId) {
        return new Profile(userId, "Alice");
    }
    private EnrichedProfile enrichProfile(Profile profile) {
        return new EnrichedProfile(profile.id(), profile.name(), "enriched");
    }
    record Profile(String id, String name) {}
    record EnrichedProfile(String id, String name, String data) {
        String format() { return id + ":" + name + ":" + data; }
    }
}
```

Each `fromCallable` creates an isolated boundary. The outer chain remains non-blocking between boundaries.

## `fromRunnable` for completion-only blocking tasks

Use `fromRunnable` when the blocking call produces no value but must complete before downstream work begins.

```java
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
final class BlockingSideEffect {
    Mono<String> execute() {
        return Mono.fromRunnable(this::blockingInit)
            .subscribeOn(Schedulers.boundedElastic())
            .thenReturn("ready");
    }
    private void blockingInit() {
    }
}
```

The `blockingInit` method performs a blocking initialization with no return value. The `fromCallable` wrapper is not needed here because there is no return value to capture.

## Future-based handoff

When an external API already returns a `CompletableFuture`, use `fromFuture` to bridge without double-wrapping.

```java
import reactor.core.publisher.Mono;
import java.util.concurrent.CompletableFuture;
final class FutureBridge {
    Mono<String> loadAsync(String key) {
        CompletableFuture<String> future = asyncLookup(key);
        return Mono.fromFuture(future);
    }
    private CompletableFuture<String> asyncLookup(String key) {
        return CompletableFuture.completedFuture("value:" + key);
    }
}
```

If the future-completing thread is a blocking caller, add `subscribeOn(boundedElastic())` after `fromFuture`. If the future is completed by a non-blocking async client, no additional scheduler is needed.

## Terminal bridge cautions

`block()`, `blockFirst()`, `blockLast()`, `toIterable()`, and `toStream()` are boundary tools for the outermost imperative edge only. Never use terminal bridges inside an operator chain.

Placing blocking work inside `map(...)` occupies the reactive worker thread invisibly. Isolate it at the boundary with `fromCallable(...)` instead.

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
final class BadBlockingInChain {
    Flux<String> broken() {
        return Flux.just("a", "b")
            .map(value -> value + ":" + slowSyncCall());
    }
    private String slowSyncCall() {
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        return "result";
    }
}
final class GoodBoundaryIsolation {
    Mono<String> correct() {
        return Mono.fromCallable(() -> "result:" + slowSyncCall())
            .subscribeOn(Schedulers.boundedElastic());
    }
    private String slowSyncCall() {
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        return "result";
    }
}
```

`Mono.toFuture()` is the safest bridge when the caller already expects `CompletableFuture` semantics. If blocking is spread across `map(...)`, `flatMap(...)`, and callbacks, the chain no longer communicates where the cost lives.

## Virtual-thread considerations (Java 21+)

On Java 21+, Reactor does not use virtual threads by default. To back the shared `Schedulers.boundedElastic()` with virtual threads, start the JVM with the Reactor system property `reactor.schedulers.defaultBoundedElasticOnVirtualThreads=true`:

```bash
java -Dreactor.schedulers.defaultBoundedElasticOnVirtualThreads=true -jar app.jar
```

This does not change the bridge pattern but affects concurrency behavior:

- Virtual threads still block their carrier when calling pinning-sensitive APIs (`synchronized`, native methods).
- A high volume of `fromCallable` bridges on a virtual-thread-backed pool can create many virtual threads.
- For pinning-sensitive blocking calls, prefer a dedicated `newBoundedElastic(...)` with explicit capacity tuning rather than enabling virtual threads globally.

```java
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
final class PinnedBlockingBridge {
    Mono<byte[]> readResource(String path) {
        return Mono.fromCallable(() -> pinnedRead(path))
            .subscribeOn(Schedulers.newBoundedElastic(4, 100, "pinned-io"));
    }
    private byte[] pinnedRead(String path) {
        return new byte[0];
    }
}
```

## Failure checks

- Do not scatter multiple `subscribeOn(...)` calls and expect each one to matter independently -- place each at the real source boundary.
- Do not hide blocking work inside `map(...)` or `flatMap(...)` without a visible `fromCallable` wrapper.
- If the real blocker is scheduler policy, worker selection, or execution tracing, treat that as a scheduling problem rather than a core blocking-bridge problem.
