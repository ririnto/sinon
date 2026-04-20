---
title: "Signal-Level Diagnostics"
description: "Open this when you need to inspect signal flow, value propagation, or Context at individual pipeline stages without changing execution context or tracing assembly."
---

Open this when the pipeline is conceptually correct but you need to see what signals flow through which stage, what values each operator sees, or how Context propagates per-signal.

For assembly tracing, global hooks, or thread-hop debugging, see the `reactor-scheduling` skill's debugging reference.

## Start with the narrowest tool

| Need | Use | What it shows |
| --- | --- | --- |
| label one suspicious stage | `checkpoint("label")` | assembly traceback on error |
| inspect local signal flow | `log("category")` | every signal with default formatting |
| inspect per-signal Context | `doOnEach(...)` with `Signal.getContextView()` | metadata attached to each signal |
| conditional value inspection | `doOnNext(...)` / `doOnError(...)` | only values or only errors |

## `checkpoint(...)` for error localization

```java
import reactor.core.publisher.Flux;

final class CheckpointedPipeline {
    Flux<Integer> values() {
        return Flux.range(1, 3)
            .map(value -> value * 2)
            .checkpoint("after-doubling")
            .map(value -> {
                if (value == 4) throw new IllegalStateException("bad value");
                return value;
            });
    }
}
```

When an error occurs downstream, the checkpoint label appears in the stack trace, identifying which stage produced or last touched the failing value.

## `log(...)` for full signal visibility

```java
import reactor.core.publisher.Flux;

final class LoggedPipeline {
    Flux<Integer> values() {
        return Flux.range(1, 3).log("reactor.core.example");
    }
}
```

The category string appears in every logged line. Filter logs by category to isolate one pipeline from others running in the same process.

## `doOnEach(...)` with per-signal `ContextView`

```java
import reactor.core.publisher.Mono;

final class ContextLogging {
    Mono<String> value() {
        return Mono.just("payload")
            .doOnEach(signal -> {
                if (signal.isOnNext()) {
                    String traceId = signal.getContextView()
                        .getOrEmpty("traceId")
                        .orElse("missing");
                }
            })
            .contextWrite(context -> context.put("traceId", "t-123"));
    }
}
```

`doOnEach` fires for every signal type: onNext, onError, onComplete. Always check the signal type before accessing value or context.

## Conditional inspection without side effects

Use `doOnNext(...)` for value-only inspection when error or completion handling is not needed.

```java
import reactor.core.publisher.Flux;

final class InspectedPipeline {
    Flux<String> processed() {
        return Flux.just("a", "b", "c")
            .doOnNext(value -> System.out.println("processing: " + value))
            .map(String::toUpperCase);
    }
}
```

`doOnNext(...)` fires only for `onNext` signals, making it lighter than `doOnEach(...)` when completion and error signals do not need inspection.

## Failure checks

- Prefer a local checkpoint before enabling any global debug instrumentation.
- Remove diagnostic operators once the root cause is known -- they add overhead in production.
- If the problem is demand rather than signal content, open [Backpressure and Demand](backpressure.md).
- If the problem is where execution moves rather than what signals flow, open the `reactor-scheduling` skill's debugging reference.
