---
metadata:
  reference:
    Project Reactor Core:
      version: 3.8.7
      url:
        - https://projectreactor.io/docs/core/3.8.7/reference/aboutDoc.html
        - https://projectreactor.io/docs/core/3.8.7/reference/gettingStarted.html
        - https://projectreactor.io/docs/core/3.8.7/api/reactor/core/publisher/SynchronousSink.html
        - https://projectreactor.io/docs/core/3.8.7/api/reactor/core/publisher/Mono.html
        - https://projectreactor.io/docs/core/3.8.7/reference/debugging.html
        - https://projectreactor.io/docs/core/3.8.7/api/reactor/core/publisher/Flux.html
    Project Reactor BOM:
      version: 2025.0.7
      url: https://repo.maven.apache.org/maven2/io/projectreactor/reactor-bom/2025.0.7/reactor-bom-2025.0.7.pom
name: reactor-core
description: >-
  Design or review Reactor Flux/Mono sources, operator composition, empty/error behavior, demand, and Context.
---

# Reactor Core

## Official Baseline

- Use the official Project Reactor 3.8.x reference guide for this skill.
  - Reviewed against `reactor-core` 3.8.7 and `reactor-bom` 2025.0.7.
  These versions are documentation baselines, not dependency pins.
- Before adding or upgrading Reactor dependencies, check `io.projectreactor:reactor-bom` and `io.projectreactor:reactor-core` in Maven Central for the latest stable compatible release.
  Honor the project's existing platform, BOM, or pins and keep Reactor modules on the same managed release train.

Author the ordinary Reactor path with `Flux` and `Mono`.

## Goal

This skill covers source selection, operator composition, combination, empty/error behavior, ordinary demand decisions, and request-scoped `Context` usage.
Keep dedicated scheduler design, sink-driven hot sources, and `reactor-test` work in their own domains.

## Scope

Activate this skill for:

- choosing between `Flux<T>` and `Mono<T>`
- creating a sequence from fixed values, collections, one lazy value, a future, or one blocking call
- composing a pipeline with transformation, filtering, sequencing, merging, zipping,
  cancellation-aware fan-out, time-bounded operations, and collection operators
- making empty behavior, error translation, fallback, cleanup, completion-side repeat, bounded retry, and timeout explicit
- deciding whether ordinary backpressure defaults are enough or an overflow policy must be chosen
- carrying tracing, request, tenant, or auth metadata through `Context`

Do not activate for:

- scheduler selection as the main problem
- manual emission design with `Sinks`
- test-only work centered on `StepVerifier`, `TestPublisher`, or virtual time
- transport, framework, persistence, or web-stack integration details
- custom operator implementation or hook-heavy global instrumentation

## Coverage map

| Reactor surface | Keep in this file | Open a reference when... |
| --- | --- | --- |
| `Flux` / `Mono` choice | cardinality, contract, type-switching operators | the real problem is hot/manual sources rather than ordinary cardinality |
| source creation | `just`, `empty`, `error`, `defer`, `fromCallable`, `fromSupplier`, `fromFuture`, `fromIterable`, `range` | you need `generate`, `create`, `push`, `using`, or other programmatic patterns |
| operator composition | `map`, `flatMap`, `concatMap`, `handle`, `filter`, `take`, `skip`, `collectList`, `reduce`, `switchMap`, `flatMapMany` | ordering, batching, or async fan-out details become the blocker |
| combination | `concat`, `merge`, `zip`, `combineLatest`, `then`, `switchIfEmpty`, `switchOnNext` | exact completion or ordering semantics decide correctness |
| empty/error behavior | `defaultIfEmpty`, `switchIfEmpty`, `onErrorResume`, `onErrorMap`, `doFinally`, `timeout`, `repeat`, bounded retry | you need `Retry` policies, backoff, filtering, retry exhaustion, or repeat exhaustion rules |
| ordinary backpressure | natural demand, `limitRate(...)`, basic overflow choice | `prefetch`, `BaseSubscriber`, or queue growth becomes the blocker |
| threading / schedulers | recognize the boundary only | `publishOn(...)`, `subscribeOn(...)`, scheduler choice, or execution tracing becomes the main problem |
| blocking bridge | one blocking boundary with `Mono.fromCallable(...)` | multiple boundaries, fromRunnable/fromFuture nuances, terminal bridges, or virtual-thread considerations are needed |
| `Context` | `contextWrite(...)`, `deferContextual(...)`, ordinary metadata flow | nested writes, library-facing composition, or precedence rules are the blocker |
| assembly/thread tracing | recognize the boundary only | assembly tracing, global hooks, or thread-hop debugging becomes the main job |
| hot/cold and multicast | recognize the boundary only | repeated work, replay, or shared subscriptions decide the design |
| sink/manual hot-source APIs | recognize the boundary only | manual emission, sink flavor choice, or emit-failure behavior decides the design |
| batching/grouping/windowing | recognize the boundary only | `groupBy`, `window`, or `buffer` shapes the pipeline |
| sequence diagnostics | signal-level `checkpoint(...)`, `log(...)`, or `doOnEach(...)` as narrow tools | signal-level inspection becomes the main job |

## Operating rules

- Model cardinality first: `Mono<T>` for 0..1, `Flux<T>` for 0..N.
- Prefer the narrowest source factory that matches the real data boundary.
- Use `map(...)` for synchronous value-to-value work and `flatMap(...)` for publisher-returning work.
- Use `concatMap(...)` when downstream order must stay stable.
- Use `switchMap(...)` when previous inner publishers should cancel on new trigger (search-as-you-type, latest-win semantics).
- Use `timeout(...)` to bound the wait for any signal and fall back or error on delay.
- Make empty and error behavior explicit before returning the publisher.
- Wrap blocking work once with `Mono.fromCallable(...)` and move it off the caller thread deliberately.
- Use `Context` for cross-cutting metadata, not for primary business payload.
- Treat advanced source creation, sink-driven emission, scheduler tuning, and test-only APIs as separate blockers.

## Task Context

Read the affected source, pipeline, subscribers, and tests to establish cardinality, ordering, and terminal behavior.
Change only the operators needed for the requested contract.
Use the [reference table](#references) when demand, creation, retry, sharing, batching, blocking, or Context needs detail.
Operator order follows the required signal and subscription semantics, not a fixed template.

## Reactor quick reference

| Need | Default move | Why |
| --- | --- | --- |
| one result | `Mono<T>` | preserves 0..1 semantics directly |
| many results | `Flux<T>` | keeps demand and streaming visible |
| lazy one-shot lookup | `Mono.fromCallable(...)` | captures deferred work once per subscription |
| async step returning a publisher | `flatMap(...)` | flattens the nested publisher |
| async step with ordering | `concatMap(...)` | preserves source order |
| cancellation-aware fan-out (latest-win) | `switchMap(...)` | cancels previous inner on new trigger |
| Mono-to-Flux flatten | `flatMapMany(...)` or `flatMapIterable(...)` | bridges cardinality change |
| synchronous reshape | `map(...)` | keeps the chain simple |
| conditional single-signal emission | `handle(...)` | emit zero or one value per input element |
| static empty fallback | `defaultIfEmpty(...)` | eagerly replaces empty with one value |
| dynamic empty fallback | `switchIfEmpty(...)` | lazily switches to another publisher on empty |
| error fallback | `onErrorResume(...)` | chooses a replacement publisher |
| exception translation | `onErrorMap(...)` | preserves failure flow while changing the type |
| time-bounded operation | `timeout(Duration)` | errors or falls back if no signal arrives in time |
| completion-side repeat | `repeat(n)` or `repeatWhen(...)` | re-subscribes on completion, not error |
| sequential combination | `concat(...)` | later sources wait for earlier completion |
| concurrent combination | `merge(...)` | emits as soon as values arrive |
| pair by index | `zip(...)` | aligns values positionally |
| latest-state recompute | `combineLatest(...)` | recomputes when any source changes |
| lifecycle side effect | `doOnSubscribe` / `doOnNext` / `doOnError` / `doOnCancel` / `doFinally` | observe signals without transforming |
| request metadata | `contextWrite(...)` + `deferContextual(...)` | keeps metadata in the subscription, not the thread |

## Ready-to-adapt examples

### Ordinary `Mono` contract with blocking bridge and explicit failure path

```java
import java.io.IOException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
final class UserLookupService {
    Mono<UserView> loadUser(String userId) {
        return Mono.fromCallable(() -> fetchUser(userId))
            .subscribeOn(Schedulers.boundedElastic())
            .switchIfEmpty(Mono.error(new IllegalStateException("Missing user: " + userId)))
            .map(user -> new UserView(user.id(), user.name()))
            .onErrorMap(IOException.class, error -> new IllegalStateException("User lookup failed", error));
    }
    private User fetchUser(String userId) throws IOException {
        return new User(userId, "Ada");
    }
    record User(String id, String name) {}
    record UserView(String id, String name) {}
}
```

One scheduler hop offloads the blocking call to `boundedElastic`.
Multi-hop placement or custom scheduler design is scheduler-specific work outside this core pipeline path.

### `Flux` pipeline with ordered async fan-out and empty fallback

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
final class ActivityService {
    Flux<String> recentActions(String userId) {
        return findActionIds(userId)
            .concatMap(this::fetchAction)
            .filter(action -> !action.isBlank())
            .switchIfEmpty(Flux.just("NO_ACTIONS"));
    }
    private Flux<String> findActionIds(String userId) {
        return Flux.just("login", "purchase", "logout");
    }
    private Mono<String> fetchAction(String actionId) {
        return Mono.just(actionId.toUpperCase());
    }
}
```

### Ordinary `Context` usage for request metadata

```java
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
final class TraceAwareHandler {
    Mono<String> handle(String input) {
        return Mono.deferContextual(context -> Mono.just(context.get("traceId") + ":" + input))
            .contextWrite(Context.of("traceId", "trace-123"));
    }
}
```

### `switchMap` for cancellation-aware fan-out

```java
import reactor.core.publisher.Flux;
import java.time.Duration;
final class SearchAsYouType {
    Flux<String> search(Flux<String> queries) {
        return queries.switchMap(query ->
            fetchResults(query).take(Duration.ofMillis(200))
        );
    }
    private Flux<String> fetchResults(String query) {
        return Flux.just(query + "-result1", query + "-result2");
    }
}
```

`switchMap` cancels the previous inner publisher when a new trigger arrives.
Use it for latest-win scenarios like search-as-you-type or live configuration refresh.

### `timeout` with fallback

```java
import java.time.Duration;
import reactor.core.publisher.Mono;
final class BoundedCall {
    Mono<String> fetchWithTimeout() {
        return remoteCall()
            .timeout(Duration.ofSeconds(3), Mono.just("fallback-value"));
    }
    private Mono<String> remoteCall() {
        return Mono.just("response").delayElement(Duration.ofMillis(100));
    }
}
```

### `repeat` for completion-side re-subscription

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
final class PollingService {
    Flux<String> pollThreeTimes() {
        return fetchStatus()
            .repeat(2)
            .filter(status -> "DONE".equals(status))
            .take(1);
    }
    private Mono<String> fetchStatus() {
        return Mono.just("PENDING");
    }
}
```

`repeat` re-subscribes on completion (not error).
Combine with `take(n)` or `repeatUntil(...)`.
When using `repeatWhen(...)`, its companion must include a terminating bound or predicate.

### `handle` for conditional single-signal emission

```java
import reactor.core.publisher.Flux;
final class ConditionalEmission {
    Flux<Integer> positive(Flux<Integer> source) {
        return source.handle((value, sink) -> {
            if (value > 0) {
                sink.next(value);
            }
        });
    }
}
```

The `handle` callback may emit zero or one value per input element.
Values less than or equal to zero are silently filtered (no signal emitted).
Use `flatMapIterable(...)` or `flatMap(...)` when one input must produce multiple values.

### `flatMapMany` for Mono-to-Flux cardinality change

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.List;
final class BatchExpand {
    Flux<String> expandBatch(Mono<List<String>> batchLoader) {
        return batchLoader.flatMapMany(Flux::fromIterable);
    }
}
```

Use `flatMapMany` when a `Mono<T>` produces a collection that should be emitted as individual elements in a `Flux`.

## Common pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| using `map(...)` for async work | nests publishers or hides concurrency | use `flatMap(...)` or `concatMap(...)` |
| returning `Mono<List<T>>` for a streaming contract | hides streaming semantics and demand | use `Flux<T>` unless the collection itself is the single value |
| placing blocking I/O inside `map(...)` | occupies the current worker thread invisibly | isolate it with `fromCallable(...)` |
| using `retry()` without a bound or policy | can loop forever under outage | use bounded retry or a deliberate `Retry` policy |
| using `repeat()` without a termination condition | re-subscribes forever on every completion | combine with `take(n)`, `repeatUntil(...)`, or a terminating `repeatWhen(...)` companion |
| using `timeout(...)` without fallback | the bounded timeout emits a raw `TimeoutException` downstream | handle that error or provide a fallback via `timeout(duration, fallback)` |
| treating `Context` like mutable shared state | writes are immutable and per subscription | write a new `Context` and read it with `deferContextual(...)` |
| choosing `merge(...)` when order matters | output order becomes unstable | use `concat(...)` or `concatMap(...)` |
| using programmatic creation for ordinary values | makes the source harder to reason about | stay with factory methods until a blocker exists |
| using `switchMap(...)` when all inner results matter | cancels previous inners before they complete | use `flatMap(...)` or `concatMap(...)` instead |

## Completion

Verify the changed publisher contract with affected native tests and reuse existing coverage when sufficient.
Review the relevant cardinality, ordering, empty/error, cancellation, cleanup, demand, and Context behavior.
Do not require every operator pattern or exactly one reference for each task.

## References

| Open this when... | Reference |
| --- | --- |
| factory methods are not enough and you must emit programmatically or bind resource lifetime | [Programmatic Sequence Creation](references/creation-patterns.md) |
| the exact choice among `concat`, `merge`, `zip`, `combineLatest`, or `then` changes correctness | [Combining Operators](references/combining-operators.md) |
| demand, `prefetch`, `BaseSubscriber`, or overflow policy is the blocker | [Backpressure and Demand](references/backpressure.md) |
| `Context` behavior across nested composition or library-facing code becomes the blocker | [Context for Library-Facing Flows](references/context-propagation.md) |
| plain `retry(n)` is not enough and you need backoff, filtering, or retry exhaustion rules | [Retry Strategies](references/retry-strategies.md) |
| shared subscriptions, replay, or hot/cold behavior changes the design | [Hot, Cold, and Multicasting](references/hot-cold-and-multicasting.md) |
| `groupBy`, `window`, or `buffer` shapes the pipeline and the flow stalls or grows unexpectedly | [Batching, Grouping, and Windowing](references/batching-grouping-windowing.md) |
| one blocking boundary is not enough and you need multiple boundaries, fromRunnable/fromFuture bridges, terminal edges, or virtual-thread considerations | [Advanced Blocking Bridges](references/blocking-bridges.md) |
| signal-level inspection (per-signal Context, conditional value inspection, tap observer) is needed | [Signal-Level Diagnostics](references/debugging-and-observability.md) |
| assembly tracing, global hooks, or thread-hop debugging is the real blocker | Open the `reactor-scheduling` skill. |

## Result

Complete the authorized change and explain the source, operator, and runtime decisions that affect its contract.
Report the checks run and any unresolved demand, lifecycle, or integration boundary.
