---
title: "Concurrent and Unsafe Emission"
description: "Open this when multiple producers contend for the same sink or internal performance pressure makes Sinks.unsafe() a serious option."
---

Open this when the sink shape is right but emission contention or internal synchronization cost is the blocker.

## Safe sinks detect non-serialized emission

```java
import reactor.core.publisher.Sinks;

final class SafeSinkContention {
    Sinks.EmitResult emit(Sinks.Many<String> sink, String value) {
        return sink.tryEmitNext(value);
    }
}
```

When multiple producers race, a safe sink can return `FAIL_NON_SERIALIZED`.

## Busy-loop retry for contention

```java
import java.time.Duration;
import reactor.core.publisher.Sinks;

final class BusyLoopRetry {
    void publish(Sinks.Many<String> sink, String value) {
        sink.emitNext(value, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(200)));
    }
}
```

## `Sinks.unsafe()` boundary

```java
import reactor.core.publisher.Sinks;

final class UnsafeSinkFactory {
    Sinks.Many<String> sink() {
        return Sinks.unsafe().many().multicast().onBackpressureBuffer();
    }
}
```

## Guardrails

- Prefer safe sinks unless you control emission serialization yourself.
- `Sinks.unsafe()` is an internal optimization boundary, not a public API default.
- Busy-loop retry is for short-lived contention, not for hiding a broken producer design.
- If the real blocker is emission outcome handling rather than producer contention, open [Sink Emission Failures](sink-failures.md).
