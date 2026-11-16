# Reactor Sinks Selection

## Coverage map

| Reactor hot-source surface | Common path | Seek more detail when... |
| --- | --- | --- |
| hot vs cold distinction | ordinary difference between per-subscription work and shared/manual hot sources | the design depends on connection lifecycle rather than sink emission |
| when to choose Sinks | choose Sinks for programmatic emission beyond sharing a cold source | the real problem is connect/disconnect and subscriber rendezvous |
| sink shape | `Sinks.one()`, `Sinks.empty()`, `Sinks.many()` | internal contention or unsafe sink use becomes the blocker |
| fan-out model | unicast vs multicast vs replay | replay policy or connection lifecycle becomes the blocker |
| emission API | `tryEmit*` vs `emit*`, `EmitResult`, `EmitFailureHandler` | failure handling rules become the main problem |
| backpressure implications | ordinary queueing, dropping, or replay behavior | overflow and concurrent emission are the main blockers |
| ConnectableFlux boundary | recognize when `publish()`, `replay()`, `autoConnect(...)`, or `refCount(...)` is a better fit than Sinks | connection management is the design problem |
| concurrency boundary | recognize that safe sinks detect non-serialized emission | external synchronization or `Sinks.unsafe()` becomes the blocker |

## Multicast and replay choice points

| If you need... | Use | Trade-off |
| --- | --- | --- |
| every active subscriber to stay aligned | `directAllOrNothing()` | one slow subscriber can cause drops for all |
| fast subscribers to keep flowing while slow ones miss values | `directBestEffort()` | delivery diverges across subscribers |
| demand-aware buffering for subscribers | `onBackpressureBuffer()` | retains elements in memory |
| detect a no-demand overflow at the producer boundary | `tryEmitNext(...)` with `directAllOrNothing()` | inspect `FAIL_OVERFLOW` and choose the caller's response |
| late subscribers to see the full retained stream | `replay().all()` | retention grows unless bounded externally |
| late subscribers to see only the current state | `replay().latest()` | retains the latest emitted value; `replay().latestOrDefault(default)` supplies a value before the first emission, while `replay().limit(1)` and `replay().latest()` replay the same latest value after emission |
| late subscribers to see bounded recent history | `replay().limit(...)` | history is explicit by size or time |
