---
title: "Sink Emission Failures"
description: "Open this when EmitResult handling, retry policy, overflow, or terminated/cancelled emission is the blocker."
---

Open this when the sink type is already correct but emission outcomes and retry behavior are the real problem.

## `tryEmit*` result handling with real branching logic

```java
import reactor.core.publisher.Sinks;

final class EmitResultBranching {
    void publish(Sinks.Many<String> sink, String value) {
        Sinks.EmitResult result = sink.tryEmitNext(value);
        if (result.isFailure()) {
            handleFailure(result);
        }
    }

    private void handleFailure(Sinks.EmitResult result) {
        switch (result) {
            case FAIL_OVERFLOW -> System.err.println("downstream cannot keep up -- consider backpressure policy change");
            case FAIL_CANCELLED -> System.out.println("consumer cancelled -- stop emitting");
            case FAIL_TERMINATED -> System.out.println("sink already completed -- need a new sink instance");
            case FAIL_NON_SERIALIZED -> throw new IllegalStateException("concurrent emission detected -- coordinate producers");
            case FAIL_ZERO_SUBSCRIBER -> System.out.println("no subscriber yet -- value dropped");
            default -> System.err.println("unexpected failure: " + result);
        }
    }
}
```

Each `EmitResult` variant requires a different recovery strategy. Branching on the specific failure type avoids both silent data loss and inappropriate retries.

## `EmitResult` quick guide

| Result | Meaning | Typical response |
| --- | --- | --- |
| `OK` | emission succeeded | continue |
| `FAIL_ZERO_SUBSCRIBER` | no subscriber and no buffer path handled the value | subscribe first or choose a buffering/replay strategy |
| `FAIL_OVERFLOW` | downstream capacity was exceeded | change backpressure policy or buffer design |
| `FAIL_CANCELLED` | the sink was interrupted by cancellation | stop emitting or wait for a new consumer |
| `FAIL_TERMINATED` | the sink already completed or errored | create a new sink |
| `FAIL_NON_SERIALIZED` | concurrent emission violated serialization | coordinate producers or use a dedicated strategy |

## `emit*` with a failure handler

```java
import reactor.core.publisher.Sinks;

final class RetriedEmission {
    void publish(Sinks.Many<String> sink, String value) {
        sink.emitNext(value, (signalType, emitResult) -> emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED);
    }
}
```

Use `FAIL_FAST` when no retry should happen.

```java
import reactor.core.publisher.Sinks;

final class FailFastEmission {
    void publish(Sinks.Many<String> sink, String value) {
        sink.emitNext(value, Sinks.EmitFailureHandler.FAIL_FAST);
    }
}
```

## Overflow and termination checks

- `FAIL_OVERFLOW` means the current sink flavor or queue policy is insufficient for downstream demand.
- `FAIL_TERMINATED` means the sink lifecycle is over. Emitting again is a new-stream problem, not a retry problem.
- `FAIL_CANCELLED` means consumers are gone or the emission path was interrupted.

## Guardrails

- Use `tryEmit*` when the caller can branch immediately on the outcome.
- Use `emit*` only when retry semantics are deliberate.
- If the recurring failure is `FAIL_NON_SERIALIZED`, open [Concurrent and Unsafe Emission](concurrent-and-unsafe-emission.md).
- If the real issue is replay or connection lifecycle rather than emission outcome, open [Connectable Flux Patterns](connectable-patterns.md).
