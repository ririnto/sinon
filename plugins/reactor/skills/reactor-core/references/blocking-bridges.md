---
title: "Blocking Bridges"
description: "Open this when you must adapt blocking or thread-affine imperative APIs into Reactor without hiding the cost or smearing scheduler decisions across the chain."
---

Open this when blocking I/O or a thread-affine API must cross into a Reactor pipeline.

## Default bridge

Wrap the blocking call once, at the boundary, and move it deliberately.

```java
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

final class BlockingBridge {
    Mono<String> readFile() {
        return Mono.fromCallable(this::blockingRead)
            .subscribeOn(Schedulers.boundedElastic());
    }

    private String blockingRead() {
        return "data";
    }
}
```

## Bridge table

| Need | Use | Boundary rule |
| --- | --- | --- |
| one blocking call returning one value | `Mono.fromCallable(...)` | isolate the call once |
| completion-only blocking task | `Mono.fromRunnable(...)` | keep side effects explicit |
| future-based handoff | `Mono.fromFuture(...)` / `toFuture()` | keep the reactive/imperative edge visible |
| imperative terminal read | `block()`, `blockFirst()`, `blockLast()` | use only at the outermost imperative edge |

## Thread-affinity handoff

Use `publishOn(...)` only when downstream work must continue on a different executor after the source boundary is already correct.

```java
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

final class ThreadAffinity {
    Flux<Integer> pipeline() {
        return Flux.range(1, 3)
            .publishOn(Schedulers.single())
            .map(value -> value * 2);
    }
}
```

## Terminal bridge cautions

- `block()`, `blockFirst()`, `blockLast()`, `toIterable()`, and `toStream()` are boundary tools, not ordinary in-chain moves.
- `Mono.toFuture()` is the safest bridge when the caller already expects `CompletableFuture` semantics.
- If blocking is spread across `map(...)`, `flatMap(...)`, and callbacks, the chain no longer communicates where the cost lives.

## Failure checks

- Do not scatter multiple `subscribeOn(...)` calls and expect each one to matter.
- Do not hide blocking work inside `map(...)` or `flatMap(...)` without a visible boundary.
- If the real blocker is scheduler policy, worker selection, or execution tracing, treat that as a scheduling problem rather than a bridge problem.
