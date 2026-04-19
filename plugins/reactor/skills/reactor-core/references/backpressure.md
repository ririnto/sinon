---
title: "Backpressure and Demand"
description: "Open this when demand, prefetch, BaseSubscriber, or overflow policy is the blocker rather than the business transform itself."
---

Open this when the pipeline is functionally correct but producer speed, request shape, or queue growth is the real failure mode.

## Inspect these questions first

1. Who requests data, and in what batch size?
2. Is demand naturally bounded, or is the chain effectively unbounded?
3. Does an operator reshape demand through `prefetch`, buffering, or inner subscriptions?
4. If the source can outrun downstream, did you choose retention, dropping, or latest-value behavior deliberately?

## Demand-shaping table

| Need | Use | What it changes |
| --- | --- | --- |
| smaller downstream request windows | `limitRate(...)` | reduces large request bursts |
| cap total consumption | `limitRequest(...)` | completes after the limit is reached |
| explicit subscriber-controlled demand | `BaseSubscriber` | lets downstream request manually |
| retain overflow | `onBackpressureBuffer(...)` | trades memory for delivery |
| drop overflow | `onBackpressureDrop(...)` | preserves throughput by losing elements |
| keep only the newest item | `onBackpressureLatest()` | turns the stream into state-like sampling |
| listener bridge overflow policy | `FluxSink.OverflowStrategy` | makes `create(...)` or `push(...)` semantics explicit |

## `BaseSubscriber` for manual demand

```java
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

final class DemandControlledConsumer {
    void consume() {
        Flux.range(1, 10).subscribe(new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                request(2);
            }

            @Override
            protected void hookOnNext(Integer value) {
                request(1);
            }
        });
    }
}
```

## `limitRate(...)` as the ordinary shaping move

```java
import reactor.core.publisher.Flux;

final class RateLimitedPipeline {
    Flux<Integer> values() {
        return Flux.range(1, 500).limitRate(64);
    }
}
```

## Overflow policy for a fast source

```java
import java.time.Duration;
import reactor.core.publisher.Flux;

final class OverflowPolicy {
    Flux<Long> latestTicks() {
        return Flux.interval(Duration.ofMillis(5))
            .onBackpressureLatest();
    }
}
```

## `create(...)` overflow choices

| Strategy | Meaning | Typical fit |
| --- | --- | --- |
| `BUFFER` | retain all signals until downstream catches up | bounded or low-rate listener with acceptable memory tradeoff |
| `DROP` | drop signals when there is no demand | live updates where gaps are acceptable |
| `LATEST` | keep only the newest signal | state snapshots or telemetry |
| `ERROR` | fail on overflow | loss is unacceptable and the producer must be fixed |
| `IGNORE` | bypass downstream demand | almost never the right default |

## Failure checks

- Buffering hides a rate mismatch only until memory becomes the failure mode.
- `prefetch` can change request shape even when code looks sequential.
- `groupBy(...)` can stall when many groups are created and not consumed. Open [Batching, Grouping, and Windowing](batching-grouping-windowing.md) for that blocker.
- If the real issue starts at a manual source, inspect its `create(...)` or `push(...)` contract and overflow policy together.
