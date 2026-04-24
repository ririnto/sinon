---
name: reactor-scheduling
description: >-
  Design Reactor execution context with explicit scheduler choice, publishOn/subscribeOn placement, and blocking-boundary decisions. Use this skill when designing or reviewing Reactor execution context decisions: scheduler choice, publishOn/subscribeOn placement, blocking offload, thread-affinity boundaries, and ordinary scheduling diagnostics.
metadata:
  title: "Reactor Scheduling"
  official_project_url: "https://projectreactor.io/docs/core/release/reference/"
  reference_doc_urls:
    - "https://projectreactor.io/docs/core/release/reference/"
    - "https://projectreactor.io/docs/core/release/api/"
  version: "3.7"
  dependencies:
    - "io.projectreactor:reactor-core:3.7.x"
---

Choose execution context deliberately in Reactor.

This skill covers the ordinary path for scheduler choice, `publishOn(...)` vs `subscribeOn(...)`, blocking offload, thread-affinity boundaries, and local scheduling diagnostics. Keep custom scheduler factories, automatic context propagation, global hooks, and test-only virtual-time work in blocker references or sibling skills.

## Use this skill when

- deciding where a Reactor chain should run
- choosing among `Schedulers.parallel()`, `boundedElastic()`, `single()`, or `immediate()`
- deciding whether `publishOn(...)` or `subscribeOn(...)` is the right move
- isolating one blocking boundary without smearing thread switches across the chain
- checking whether thread-local assumptions break once the pipeline becomes asynchronous
- diagnosing thread hops or execution placement with local Reactor tools

## Do not use this skill for

- `Flux` / `Mono` source creation and ordinary operator composition as the main problem
- sink design, manual emission APIs, or replay/multicast policy
- `reactor-test` design as the main job
- framework-specific thread models such as Spring, Netty, or messaging runtimes
- custom operator authoring

## Coverage map

| Reactor scheduling surface | Keep in this file | Open a reference when... |
| --- | --- | --- |
| default execution model | source runs on the subscription thread until a scheduler changes it | you are debugging assembly/runtime gaps across many chains |
| scheduler choice | `parallel`, `boundedElastic`, `single`, `immediate` | shared defaults are not enough and you must create, tune, or replace schedulers explicitly |
| `publishOn(...)` vs `subscribeOn(...)` | ordinary placement, effect, and when both are justified | hook-level scheduling instrumentation or shared scheduler replacement becomes the blocker |
| blocking offload | one blocking boundary with `Mono.fromCallable(...)` + `subscribeOn(boundedElastic())` | queue caps, lifecycle, virtual threads, or custom executor bridges matter |
| thread-affinity and context boundary | `Context` flows with the subscription, not the thread | `ThreadLocal` bridging or automatic context propagation becomes the blocker |
| local scheduling diagnostics | `log(...)`, named `checkpoint(...)`, and visible thread logging | global hooks, assembly tracing, or debug-agent level tooling becomes the blocker |
| virtual time boundary | recognize that time control belongs to testing | time simulation becomes the main job |

## Operating rules

- Reactor is concurrency-agnostic until you introduce a `Scheduler`.
- The source and upstream chain run on the thread that performs `subscribe()` unless you move them.
- Use `publishOn(...)` to switch downstream execution from that point onward.
- Use `subscribeOn(...)` to move subscription and upstream source work.
- Place `subscribeOn(...)` at the source boundary, especially for blocking bridges.
- Use `Schedulers.parallel()` for fast non-blocking CPU work.
- Use `Schedulers.boundedElastic()` for blocking I/O or thread-affine imperative code.
- Treat `Context` as subscription metadata, not as a thread-local replacement by itself.
- Prefer the fewest scheduler hops that preserve correctness.

## Decision path

1. Start with the default model.
   - If no scheduler is introduced, work stays on the subscription thread.
2. Choose the workload fit.
   - CPU-bound and non-blocking: `Schedulers.parallel()`.
   - Blocking I/O or thread-affine bridge: `Schedulers.boundedElastic()`.
   - Single-thread affinity: `Schedulers.single()` or a dedicated custom scheduler.
   - No-op handoff or test-local immediacy: `Schedulers.immediate()`.
3. Choose the placement.
   - Upstream source or blocking bridge must move: `subscribeOn(...)`.
   - Downstream section must move: `publishOn(...)`.
   - Both are valid only when upstream and downstream need different contexts.
4. Check thread-local assumptions.
   - If logic depends on request metadata, move it through `Context` rather than thread locals.
5. Debug locally first.
   - Use `log(...)`, named `checkpoint(...)`, and explicit thread logging before reaching for global hooks.
6. Open a reference only when the blocker is custom scheduler design, automatic context propagation, or global diagnostics.

## Ordinary workflow

1. State which part of the pipeline must move and why.
2. Pick the narrowest scheduler that matches the workload.
3. Place `subscribeOn(...)` at the source if the source or blocking bridge must move.
4. Add `publishOn(...)` only where downstream affinity truly changes.
5. Keep blocking work wrapped once and offloaded explicitly.
6. If request metadata must survive thread hops, keep it in `Context`.
7. Verify actual thread placement with local diagnostics before adding deeper tooling.

## Scheduler quick reference

| Need | Default move | Why |
| --- | --- | --- |
| CPU-bound non-blocking work | `Schedulers.parallel()` | fixed worker pool sized for CPU work |
| blocking I/O or legacy bridge | `Schedulers.boundedElastic()` | bounded worker expansion for blocking tasks |
| one serialized execution lane | `Schedulers.single()` | preserves one-thread affinity |
| no real handoff (test or caller-thread only) | `Schedulers.immediate()` | runs on the current thread; avoid in production pipelines |
| move source and subscription | `subscribeOn(...)` | affects upstream work |
| move downstream operators | `publishOn(...)` | affects work after that operator |
| request metadata across async boundaries | `contextWrite(...)` + `deferContextual(...)` | survives thread switches without thread-local assumptions |

## Ready-to-adapt examples

### Blocking boundary at the source

```java
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

final class BlockingLookup {
    Mono<String> readUser(String userId) {
        return Mono.fromCallable(() -> blockingLookup(userId))
            .subscribeOn(Schedulers.boundedElastic())
            .map(String::trim);
    }
    private String blockingLookup(String userId) {
        return "user-" + userId;
    }
}
```

### Downstream handoff with `publishOn(...)`

```java
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

final class PublishOnExample {
    Flux<String> process() {
        return Flux.just("a", "b", "c")
            .map(String::toUpperCase)
            .publishOn(Schedulers.parallel())
            .map(value -> value + "-done");
    }
}
```

### Different upstream and downstream execution contexts

```java
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

final class MixedExecutionExample {
    Mono<String> loadAndTransform(String userId) {
        return Mono.fromCallable(() -> blockingLookup(userId))
            .subscribeOn(Schedulers.boundedElastic())
            .publishOn(Schedulers.parallel())
            .map(String::toUpperCase);
    }
    private String blockingLookup(String userId) {
        return "user-" + userId;
    }
}
```

### Context survives thread hops

```java
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

final class ContextAcrossThreads {
    Mono<String> handle(String input) {
        return Mono.deferContextual(context -> Mono.just(context.get("requestId") + ":" + input))
            .publishOn(Schedulers.parallel())
            .contextWrite(Context.of("requestId", "req-42"));
    }
}
```

## Common pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| using `publishOn(...)` to move a blocking source | the source still runs before the handoff | wrap the source and use `subscribeOn(...)` |
| stacking many `publishOn(...)` calls | adds context switches without adding correctness | keep only the handoffs that change behavior |
| using `parallel()` for blocking I/O | blocks non-blocking worker threads | use `boundedElastic()` |
| assuming thread locals survive scheduler hops | the thread can change between signals | move request data through `Context` |
| adding multiple `subscribeOn(...)` calls for control | only the closest relevant source placement matters | place one `subscribeOn(...)` at the real boundary |
| placing `subscribeOn(...)` inside a `flatMap` lambda to affect the outer chain | `subscribeOn` inside `flatMap` scopes to the inner publisher only | place `subscribeOn` at the outer source or on the inner publisher deliberately |
| using `Schedulers.immediate()` in production code | runs on the caller thread with no isolation | reserve for test code or when you explicitly want caller-thread execution |
| placing `contextWrite(...)` downstream of `publishOn(...)` | `contextWrite` affects upstream operators; placement after a thread switch may not reach intended targets | place `contextWrite` before `publishOn` when upstream needs the context |

## Validation checklist

- [ ] The ordinary path explains the default execution model before any scheduler is introduced.
- [ ] Scheduler choice matches workload type and blocking behavior.
- [ ] `publishOn(...)` and `subscribeOn(...)` are used for the correct direction of influence.
- [ ] Blocking work is wrapped once and offloaded explicitly.
- [ ] `Context` is described as subscription metadata rather than thread-local state.
- [ ] Local diagnostics are enough to verify ordinary thread placement.
- [ ] Advanced scheduler customization, automatic context propagation, and global debugging are routed to references.
- [ ] The ordinary path is understandable from this file alone.

## References

| Open this when... | Reference |
| --- | --- |
| shared scheduler defaults are not enough and you must create, tune, or replace schedulers explicitly | [Scheduler Tuning and Custom Schedulers](references/scheduler-tuning.md) |
| `ThreadLocal`-backed data must cross scheduler boundaries or automatic context propagation becomes the blocker | [ThreadLocal Context Bridging](references/threadlocal-context-bridging.md) |
| local `log(...)` and `checkpoint(...)` are not enough and you need global debugging hooks or assembly tracing | [Debugging and Hooks](references/debugging-and-hooks.md) |

## Output contract

Return:

1. The workload-to-scheduler choice.
2. The chosen `publishOn(...)` / `subscribeOn(...)` placement and why it changes execution correctly.
3. Any blocking boundary or `Context` rule that changes runtime behavior.
4. Any blocker that requires opening exactly one reference.
