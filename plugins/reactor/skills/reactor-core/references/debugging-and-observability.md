---
title: "Debugging and Observability"
description: "Open this when the pipeline behaves differently at assembly and runtime and you need checkpoints, signal logging, or sequence-level diagnostics."
---

Open this when the pipeline is conceptually right but failure location, signal flow, or assembly context is unclear.

## Start with the narrowest tool

| Need | Use | Cost |
| --- | --- | --- |
| label one suspicious stage | `checkpoint("label")` | low |
| inspect local signal flow | `log("category")` | low to medium |
| inspect every signal with context | `doOnEach(...)` | medium |
| capture assembly traces broadly | `Hooks.onOperatorDebug()` | high |

## `checkpoint(...)`

```java
import reactor.core.publisher.Flux;

final class CheckpointedPipeline {
    Flux<Integer> values() {
        return Flux.range(1, 3)
            .map(value -> value * 2)
            .checkpoint("after-doubling");
    }
}
```

## `log(...)`

```java
import reactor.core.publisher.Flux;

final class LoggedPipeline {
    Flux<Integer> values() {
        return Flux.range(1, 3).log("reactor.core.example");
    }
}
```

## `doOnEach(...)` with `ContextView`

```java
import reactor.core.publisher.Mono;

final class ContextLogging {
    Mono<String> value() {
        return Mono.just("payload")
            .doOnEach(signal -> {
                if (signal.isOnNext()) {
                    signal.getContextView().getOrEmpty("traceId");
                }
            });
    }
}
```

## Failure checks

- Prefer a local checkpoint before enabling global debug hooks.
- Remove broad instrumentation once the root cause is known.
- If the problem is demand rather than location, open [Backpressure and Demand](backpressure.md).
- If the real blocker is scheduler policy or thread switching, treat that as a scheduling problem rather than a sequence-diagnostics problem.
