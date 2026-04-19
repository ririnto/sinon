---
title: "Combining Operators"
description: "Open this when the exact choice among concat, merge, zip, combineLatest, or then determines correctness, ordering, or completion behavior."
---

Open this when the blocker is combination semantics rather than the business logic inside each publisher.

## Operator selection

| If you need... | Use | What decides correctness |
| --- | --- | --- |
| preserve full source order and subscribe sequentially | `concat(...)` | later sources wait for earlier completion |
| preserve order but delay propagated errors | `concatDelayError(...)` | failures are postponed until all sources finish |
| interleave values as soon as they arrive | `merge(...)` | output order is unstable |
| subscribe eagerly but emit in source order | `mergeSequential(...)` | concurrency is allowed without reordering outputs |
| pair values by position | `zip(...)` | the shortest source controls completion |
| recompute from the latest value of each source | `combineLatest(...)` | old values are reused in later combinations |
| ignore emitted values and wait for completion only | `then(...)` / `thenMany(...)` | value signals are discarded |

## `concat(...)`

```java
import reactor.core.publisher.Flux;

final class ConcatExample {
    Flux<String> orderedAuditTrail() {
        return Flux.concat(Flux.just("load"), Flux.just("transform"), Flux.just("store"));
    }
}
```

## `merge(...)`

```java
import java.time.Duration;
import reactor.core.publisher.Flux;

final class MergeExample {
    Flux<String> liveFeed() {
        Flux<String> notifications = Flux.just("notice-1", "notice-2").delayElements(Duration.ofMillis(20));
        Flux<String> updates = Flux.just("update-1", "update-2").delayElements(Duration.ofMillis(10));
        return Flux.merge(notifications, updates);
    }
}
```

## `zip(...)`

```java
import reactor.core.publisher.Flux;

final class ZipExample {
    Flux<String> labeledUsers() {
        return Flux.zip(Flux.just("A", "B"), Flux.just(1, 2), (name, rank) -> name + "-" + rank);
    }
}
```

## `combineLatest(...)`

```java
import java.time.Duration;
import reactor.core.publisher.Flux;

final class CombineLatestExample {
    Flux<String> dashboard() {
        Flux<String> status = Flux.just("GREEN", "YELLOW").delayElements(Duration.ofMillis(30));
        Flux<Integer> load = Flux.just(10, 20, 30).delayElements(Duration.ofMillis(20));
        return Flux.combineLatest(status, load, (currentStatus, currentLoad) -> currentStatus + ":" + currentLoad);
    }
}
```

## `then(...)`

```java
import reactor.core.publisher.Mono;

final class ThenExample {
    Mono<String> writeAndConfirm() {
        return Mono.fromRunnable(() -> {})
            .then(Mono.just("stored"));
    }
}
```

## Failure checks

- If output order matters, rule out `merge(...)` first.
- If one source may complete empty, remember that `zip(...)` can complete before other sources produce all values.
- If you only need a completion dependency, prefer `then(...)` or `thenMany(...)` over `zip(...)` or `merge(...)`.
- If the issue is actually partitioning one stream into groups, windows, or buffers, open [Batching, Grouping, and Windowing](batching-grouping-windowing.md).
