---
title: "Concurrent and Unsafe Emission"
description: "Open this when multiple producers contend for the same sink or internal performance pressure makes Sinks.unsafe() a serious option."
---

Open this when the sink shape is right but emission contention or internal synchronization cost is the blocker.

## Safe sinks detect non-serialized emission

When multiple producers race, a safe sink returns `FAIL_NON_SERIALIZED` instead of silently corrupting state.

```java
import reactor.core.publisher.Sinks;

final class SafeSinkContention {
    Sinks.EmitResult emit(Sinks.Many<String> sink, String value) {
        return sink.tryEmitNext(value);
    }
}
```

## When contention indicates a design problem

Contention on a safe sink usually means the producer side needs coordination, not a weaker sink. Prefer explicit synchronization or a single-emitter design before reaching for `Sinks.unsafe()`.

```java
import reactor.core.publisher.Sinks;
import java.util.concurrent.locks.ReentrantLock;

final class CoordinatedEmission {
    private final Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
    private final ReentrantLock lock = new ReentrantLock();

    void safePublish(String value) {
        lock.lock();
        try {
            sink.tryEmitNext(value);
        } finally {
            lock.unlock();
        }
    }
}
```

External serialization avoids `FAIL_NON_SERIALIZED` entirely and keeps the sink in its fast path.

## Busy-loop retry for short-lived contention

When contention is brief and infrequent (e.g., bursty events from a limited set of producers), busy-loop retry is acceptable.

```java
import java.time.Duration;
import reactor.core.publisher.Sinks;

final class BusyLoopRetry {
    void publish(Sinks.Many<String> sink, String value) {
        sink.emitNext(value, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(200)));
    }
}
```

The handler retries on `FAIL_NON_SERIALIZED` up to the specified duration before giving up. Use it only when contention windows are short and bounded.

## `Sinks.unsafe()` boundary

`Sinks.unsafe()` skips the internal serialized-access check. This is an optimization boundary, not a convenience API.

```java
import reactor.core.publisher.Sinks;

final class UnsafeSinkFactory {
    Sinks.Many<String> sink() {
        return Sinks.unsafe().many().multicast().onBackpressureBuffer();
    }
}
```

Use `Sinks.unsafe()` only when:

- You guarantee single-threaded emission externally (e.g., all emissions run inside the same event loop).
- Profiling shows that the serialization check itself is a measurable bottleneck.
- The sink is entirely internal to a component you control.

Using `Sinks.unsafe()` with actual multi-threaded contention produces undefined behavior -- silent data corruption, lost signals, or crashes.

## Guardrails

- Prefer safe sinks unless you control emission serialization yourself.
- `Sinks.unsafe()` is an internal optimization boundary, not a public API default.
- Busy-loop retry is for short-lived contention, not for hiding a broken producer design.
- If the real blocker is emission outcome handling rather than producer contention, open [Sink Emission Failures](sink-failures.md).
