---
name: reactor-sinks
description: Use this skill when designing or reviewing Reactor hot-source APIs with Sinks: sink type selection, manual emission, replay/multicast choices, emit result handling, and the boundary between Sinks and ConnectableFlux-style sharing.
metadata:
  title: "Reactor Sinks"
  official_project_url: "https://projectreactor.io/docs/core/release/reference/"
  reference_doc_urls:
    - "https://projectreactor.io/docs/core/release/reference/"
    - "https://projectreactor.io/docs/core/release/api/"
  version: "3.7"
---

Author Reactor hot sources deliberately.

This skill covers the ordinary path for choosing `Sinks.one()`, `Sinks.empty()`, or `Sinks.many()`, selecting unicast vs multicast vs replay behavior, handling `tryEmit*` / `emit*` outcomes, and deciding when a sink is the right tool instead of ConnectableFlux-style hot conversion. Keep advanced connection lifecycle, contention tuning, and internal unsafe sinks in blocker references.

## Use this skill when

- creating a programmatic hot source in Reactor
- deciding whether a sink or a shared/replayed cold source is the correct design
- choosing between `Sinks.one()`, `Sinks.empty()`, and `Sinks.many()`
- choosing unicast, multicast, or replay behavior for multiple subscribers
- handling `EmitResult`, `EmitFailureHandler`, or ordinary backpressure outcomes from manual emission
- reviewing why late subscribers miss values or replay old ones

## Do not use this skill for

- ordinary `Flux` / `Mono` composition as the main problem
- scheduler choice or thread placement as the main problem
- `reactor-test` APIs as the main job
- framework-specific event buses or transport integration details
- deprecated `Processor`-based designs

## Coverage map

| Reactor hot-source surface | Keep in this file | Open a reference when... |
| --- | --- | --- |
| hot vs cold distinction | ordinary difference between per-subscription work and shared/manual hot sources | the design depends on connection lifecycle rather than sink emission |
| when to choose Sinks | choose Sinks for programmatic emission, not just for sharing a cold source | the real problem is connect/disconnect and subscriber rendezvous |
| sink shape | `Sinks.one()`, `Sinks.empty()`, `Sinks.many()` | internal contention or unsafe sink use becomes the blocker |
| fan-out model | unicast vs multicast vs replay | replay policy or connection lifecycle becomes the blocker |
| emission API | `tryEmit*` vs `emit*`, `EmitResult`, `EmitFailureHandler` | failure handling rules become the main problem |
| backpressure implications | ordinary queueing, dropping, or replay behavior | overflow and concurrent emission are the main blockers |
| ConnectableFlux boundary | recognize when `publish()`, `replay()`, `autoConnect(...)`, or `refCount(...)` is a better fit than Sinks | connection management is the design problem |
| concurrency boundary | recognize that safe sinks detect non-serialized emission | external synchronization or `Sinks.unsafe()` becomes the blocker |

## Operating rules

- Use Sinks when you must emit programmatically into a Reactor publisher.
- Prefer `Sinks.one()` for exactly one terminal value, `Sinks.empty()` for terminal-only completion/error, and `Sinks.many()` for multi-value streams.
- Use unicast when there is exactly one subscriber, multicast for live fan-out, and replay when late subscribers must observe history.
- For multicast, choose `onBackpressureBuffer()` when retention is acceptable, `directAllOrNothing()` when all subscribers must advance together, and `directBestEffort()` when slow subscribers may miss values but fast ones should continue.
- For replay, choose whether all history, the latest signal, or a bounded size/time window should be retained.
- Use `tryEmit*` when the caller can inspect and branch on `EmitResult` immediately.
- Use `emit*` only when retry semantics are deliberate and an `EmitFailureHandler` is part of the design.
- Keep sink backpressure policy explicit at the sink flavor boundary.
- If you only need to share or replay an existing cold source, prefer ConnectableFlux-style operators over a new sink.
- Treat `Sinks.unsafe()` as an internal optimization boundary, not an ordinary default.

## Decision path

1. Decide whether you need programmatic emission.
   - If values come from imperative callbacks or external producers, use Sinks.
   - If you only need to share an existing cold source, consider `publish()`, `replay()`, `autoConnect(...)`, or `refCount(...)` instead.
2. Choose the sink shape.
   - Single result: `Sinks.one()`.
   - Terminal-only signal: `Sinks.empty()`.
   - Multiple values: `Sinks.many()`.
3. Choose the subscriber model for `Sinks.many()`.
   - One subscriber with buffering: `unicast()`.
   - Many live subscribers: `multicast()` with the right delivery strategy.
   - Late subscribers need history: `replay()` with the right retention rule.
4. Choose the emission style.
   - Immediate result and explicit branching: `tryEmitNext(...)`, `tryEmitComplete()`, `tryEmitError(...)`.
   - Controlled retry policy: `emitNext(...)`, `emitComplete(...)`, `emitError(...)` with a failure handler.
5. Check backpressure and late-subscriber behavior before returning the publisher view.
6. Open a reference only when the blocker is failure handling, connection lifecycle, or concurrent internal emission.

## Ordinary workflow

1. State whether the design is manual emission or shared cold-source conversion.
2. Pick the narrowest sink type that matches the real contract.
3. Choose unicast, multicast, or replay based on subscriber count and history needs.
4. Expose the sink as `Mono` or `Flux` through `asMono()` or `asFlux()`.
5. Pick `tryEmit*` or `emit*` deliberately and make failure behavior explicit.
6. Verify backpressure and late-subscriber behavior before finalizing the API.

## Sinks quick reference

| Need | Default move | Why |
| --- | --- | --- |
| one async result | `Sinks.one()` | one value or terminal error |
| terminal-only completion/error | `Sinks.empty()` | no payload value |
| one subscriber with buffering | `Sinks.many().unicast().onBackpressureBuffer()` | single-consumer stream |
| live fan-out with retained backlog | `Sinks.many().multicast().onBackpressureBuffer()` | broadcast current signals and buffer by demand |
| live fan-out where all subscribers move together | `Sinks.many().multicast().directAllOrNothing()` | drop for everyone if one subscriber has no demand |
| live fan-out where only slow subscribers fall behind | `Sinks.many().multicast().directBestEffort()` | keep fast subscribers flowing |
| replay all retained history | `Sinks.many().replay().all()` | late subscribers receive full retained history |
| replay only the latest signal | `Sinks.many().replay().latest()` | late subscribers see current state only |
| replay bounded history | `Sinks.many().replay().limit(...)` | retains selected size or time window |
| immediate emission result | `tryEmitNext(...)` | caller handles `EmitResult` directly |
| retry-aware emission | `emitNext(..., handler)` | retries or fails by policy |
| share existing cold source | `publish()`, `replay()`, `refCount(...)`, `autoConnect(...)` | no new sink required |

## Multicast and replay choice points

| If you need... | Use | Trade-off |
| --- | --- | --- |
| every active subscriber to stay aligned | `directAllOrNothing()` | one slow subscriber can cause drops for all |
| fast subscribers to keep flowing while slow ones miss values | `directBestEffort()` | delivery diverges across subscribers |
| demand-aware buffering for subscribers | `onBackpressureBuffer()` | retains elements in memory |
| late subscribers to see the full retained stream | `replay().all()` | retention grows unless bounded externally |
| late subscribers to see only the current state | `replay().latest()` | older values are discarded |
| late subscribers to see bounded recent history | `replay().limit(...)` | history is explicit by size or time |

## Ready-to-adapt examples

### `Sinks.one()` for one callback result

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
        sink.tryEmitValue("done");
    }
}
```

### Multicast sink for live fan-out

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

final class EventBus {
    private final Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

    Flux<String> events() {
        return sink.asFlux();
    }

    void publish(String event) {
        sink.tryEmitNext(event);
    }
}
```

### Replay sink for late subscribers

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

final class ReplayFeed {
    private final Sinks.Many<String> sink = Sinks.many().replay().limit(3);

    Flux<String> feed() {
        return sink.asFlux();
    }

    void record(String value) {
        sink.tryEmitNext(value);
    }
}
```

### `emitNext(...)` with explicit failure policy

```java
import reactor.core.publisher.Sinks;

final class RetriedEmission {
    void publish(Sinks.Many<String> sink, String value) {
        sink.emitNext(value, Sinks.EmitFailureHandler.FAIL_FAST);
    }
}
```

## Common pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| creating a sink just to share an existing cold source | adds manual-emission complexity without need | use `publish()`, `replay()`, or `refCount(...)` |
| using multicast when late subscribers need history | late subscribers only see future values | use replay |
| ignoring `EmitResult` from `tryEmit*` | failures become invisible | branch on the result or switch to `emit*` |
| using replay with no limit by default | cached history can grow without bound | choose a size or time limit deliberately |
| assuming safe sinks serialize every producer intention automatically | concurrent calls can still return `FAIL_NON_SERIALIZED` | handle contention or move to a coordinated emission strategy |

## Validation checklist

- [ ] The ordinary path explains when Sinks are preferable to ConnectableFlux-style sharing.
- [ ] Sink choice matches the real contract: one value, terminal-only, or many values.
- [ ] Unicast, multicast, or replay behavior matches subscriber count and history needs.
- [ ] `tryEmit*` vs `emit*` is a deliberate API choice.
- [ ] Backpressure and replay behavior are explicit.
- [ ] Advanced failure handling, connection lifecycle, and unsafe/concurrent emission are routed to references.
- [ ] The ordinary path is understandable from this file alone.

## References

| Open this when... | Reference |
| --- | --- |
| `EmitResult`, retry policy, overflow, or terminated/cancelled emission is the blocker | [Sink Emission Failures](references/sink-failures.md) |
| the real problem is connect, disconnect, replay, or subscriber rendezvous for a shared cold source | [Connectable Flux Patterns](references/connectable-patterns.md) |
| contention, external synchronization, or `Sinks.unsafe()` is the blocker | [Concurrent and Unsafe Emission](references/concurrent-and-unsafe-emission.md) |

## Output contract

Return:

1. The chosen sink or connectable pattern and why it fits the hot-source design.
2. The chosen subscriber model: unicast, multicast, replay, or shared cold-source conversion.
3. The emission API choice and any backpressure or replay rule that changes runtime behavior.
4. Any blocker that requires opening exactly one reference.
