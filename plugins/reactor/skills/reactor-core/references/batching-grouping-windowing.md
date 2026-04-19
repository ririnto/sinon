---
title: "Batching, Grouping, and Windowing"
description: "Open this when groupBy, window, or buffer changes the pipeline shape and the flow stalls, grows unexpectedly, or becomes hard to reason about."
---

Open this when the blocker is no longer ordinary composition but how elements are partitioned by key, size, or time.

## Choose the partitioning model

| Need | Use | Output shape | Watch for |
| --- | --- | --- | --- |
| keyed sub-streams | `groupBy(...)` | `Flux<GroupedFlux<K, T>>` | high cardinality and unconsumed groups |
| rolling publisher windows | `window(...)` | `Flux<Flux<T>>` | nested publishers must be consumed |
| concrete collections | `buffer(...)` | `Flux<List<T>>` | memory growth and delayed emission |

## `groupBy(...)`

```java
import reactor.core.publisher.Flux;

final class GroupedPipeline {
    Flux<String> grouped() {
        return Flux.just("a1", "a2", "b1")
            .groupBy(value -> value.substring(0, 1))
            .flatMap(group -> group.map(value -> group.key() + ":" + value));
    }
}
```

## `window(...)`

```java
import reactor.core.publisher.Flux;

final class WindowedPipeline {
    Flux<Integer> sums() {
        return Flux.range(1, 6)
            .window(3)
            .flatMap(window -> window.reduce(0, Integer::sum));
    }
}
```

## `buffer(...)`

```java
import java.util.List;
import reactor.core.publisher.Flux;

final class BufferedPipeline {
    Flux<List<Integer>> batches() {
        return Flux.range(1, 5).buffer(2);
    }
}
```

## Shape-specific guardrails

- `groupBy(...)` creates one stream per key, so high cardinality plus low consumer concurrency can stall the pipeline.
- `window(...)` emits publishers, not lists. If you forget to subscribe to each inner publisher, the pipeline appears idle.
- `buffer(...)` delays downstream visibility until the buffer closes or upstream completes.
- Overlapping variants such as `window(maxSize, skip)` or `buffer(maxSize, skip)` can duplicate or drop elements depending on the `skip` relationship.

## Failure checks

- If the issue is actually request pressure, open [Backpressure and Demand](backpressure.md).
- If the issue is combining multiple full publishers rather than partitioning one stream, open [Combining Operators](combining-operators.md).
- If the partitioning model is still unclear, choose the output shape first: keyed streams, nested publishers, or concrete collections.
