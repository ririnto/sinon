# Reactor Sinks Examples

## `Sinks.one()` for one callback result

```java
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
final class CallbackBridge {
    Mono<String> loadValue() {
        Sinks.One<String> sink = Sinks.one();
        completeLater(sink);
        return sink.asMono();
    }
    private void completeLater(Sinks.One<String> sink) {
        sink.emitValue("done", Sinks.EmitFailureHandler.FAIL_FAST);
    }
}
```

## Multicast sink for live fan-out

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
final class EventBus {
    private final Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();
    Flux<String> events() {
        return sink.asFlux();
    }
    void publish(String event) {
        sink.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST);
    }
}
```

## Replay sink for late subscribers

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
final class ReplayFeed {
    private final Sinks.Many<String> sink = Sinks.many().replay().limit(3);
    Flux<String> feed() {
        return sink.asFlux();
    }
    void record(String value) {
        sink.emitNext(value, Sinks.EmitFailureHandler.FAIL_FAST);
    }
}
```

## `emitNext(...)` with explicit failure policy

```java
import reactor.core.publisher.Sinks;
final class RetriedEmission {
    void publish(Sinks.Many<String> sink, String value) {
        sink.emitNext(value, Sinks.EmitFailureHandler.FAIL_FAST);
    }
}
```

## `Sinks.empty()` for terminal-only signals

```java
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
final class CompletionSignal {
    Mono<Void> shutdownSignal() {
        Sinks.Empty<Void> sink = Sinks.empty();
        triggerShutdownLater(sink);
        return sink.asMono();
    }
    private void triggerShutdownLater(Sinks.Empty<Void> sink) {
        sink.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);
    }
}
```

Use `Sinks.empty()` when the only signals are completion or error -- no payload value is carried.
Call `tryEmitEmpty()` for normal termination or `tryEmitError(...)` for abnormal termination.
Use `emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST)` only when a convenience emit API is deliberate.

## `Sinks.many().unicast()` for single-subscriber streams

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
final class UnicastStream {
    private final Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
    Flux<String> stream() {
        return sink.asFlux();
    }
    void send(String value) {
        sink.emitNext(value, Sinks.EmitFailureHandler.FAIL_FAST);
    }
}
```

Unicast allows exactly one subscriber.
A second subscription attempt receives an `IllegalStateException`.
Use it when the downstream consumer is known to be singular (e.g., a dedicated processing pipeline).
