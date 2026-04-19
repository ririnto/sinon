---
name: reactor-core
description: Author Reactor pipelines with Flux and Mono. Use when designing or reviewing Flux/Mono source creation, operator composition, combination, empty/error behavior, ordinary backpressure choices, and everyday Context usage in Project Reactor.
metadata:
  title: Reactor Core
  official_project_url: "https://projectreactor.io/docs/core/release/reference/"
  reference_doc_urls:
    - "https://projectreactor.io/docs/core/release/reference/"
    - "https://projectreactor.io/docs/core/release/api/"
  version: "3.7"
---

Author the ordinary Reactor path with `Flux` and `Mono`.

This skill covers source selection, operator composition, combination, empty/error behavior, ordinary demand decisions, and request-scoped `Context` usage. Keep dedicated scheduler design, sink-driven hot sources, and `reactor-test` work in their own domains.

## Use this skill when

- choosing between `Flux<T>` and `Mono<T>`
- creating a sequence from fixed values, collections, one lazy value, a future, or one blocking call
- composing a pipeline with transformation, filtering, sequencing, merging, zipping, and collection operators
- making empty behavior, error translation, fallback, cleanup, and bounded retry explicit
- deciding whether ordinary backpressure defaults are enough or an overflow policy must be chosen
- carrying tracing, request, tenant, or auth metadata through `Context`

## Do not use this skill for

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
| operator composition | `map`, `flatMap`, `concatMap`, `handle`, `filter`, `take`, `skip`, `collectList`, `reduce` | ordering, batching, or async fan-out details become the blocker |
| combination | `concat`, `merge`, `zip`, `combineLatest`, `then`, `switchIfEmpty` | exact completion or ordering semantics decide correctness |
| empty/error behavior | `defaultIfEmpty`, `switchIfEmpty`, `onErrorResume`, `onErrorMap`, `doFinally`, bounded retry | you need `Retry` policies, backoff, filtering, or retry exhaustion rules |
| ordinary backpressure | natural demand, `limitRate(...)`, basic overflow choice | `prefetch`, `BaseSubscriber`, or queue growth becomes the blocker |
| threading / schedulers | recognize the boundary only | `publishOn(...)`, `subscribeOn(...)`, scheduler choice, or execution tracing becomes the main problem |
| blocking bridge | one blocking boundary with `Mono.fromCallable(...)` | blocking APIs, thread affinity, or bridge semantics become the main problem |
| `Context` | `contextWrite(...)`, `deferContextual(...)`, ordinary metadata flow | nested writes, library-facing composition, or precedence rules are the blocker |
| hot/cold and multicast | recognize the boundary only | repeated work, replay, or shared subscriptions decide the design |
| sink/manual hot-source APIs | recognize the boundary only | manual emission, sink flavor choice, or emit-failure behavior decides the design |
| batching/grouping/windowing | recognize the boundary only | `groupBy`, `window`, or `buffer` shapes the pipeline |
| sequence diagnostics | local `checkpoint(...)` or `log(...)` as a narrow tool | assembly/runtime diagnosis becomes the main job |

## Operating rules

- Model cardinality first: `Mono<T>` for 0..1, `Flux<T>` for 0..N.
- Prefer the narrowest source factory that matches the real data boundary.
- Use `map(...)` for synchronous value-to-value work and `flatMap(...)` for publisher-returning work.
- Use `concatMap(...)` when downstream order must stay stable.
- Make empty and error behavior explicit before returning the publisher.
- Wrap blocking work once with `Mono.fromCallable(...)` and move it off the caller thread deliberately.
- Use `Context` for cross-cutting metadata, not for primary business payload.
- Treat advanced source creation, sink-driven emission, scheduler tuning, and test-only APIs as separate blockers.

## Decision path

1. Choose the sequence type.
   - Use `Mono<T>` for one result, empty, or error.
   - Use `Flux<T>` when multiple values may arrive.
2. Choose the source boundary.
   - Fixed value: `just`, `justOrEmpty`, `empty`, `error`.
   - Collection or range: `fromIterable`, `range`.
   - Lazy single value: `fromSupplier`, `fromCallable`.
   - Future bridge: `fromFuture`.
   - Subscription-time choice: `defer`.
3. Compose the main business path.
   - Transform: `map`, `flatMap`, `concatMap`, `handle`.
   - Filter or gate: `filter`, `take`, `skip`, `distinct`.
   - Aggregate: `collectList`, `reduce`, `count`.
4. Choose the combination behavior explicitly.
   - Sequential: `concat`, `concatWith`.
   - Concurrent/interleaved: `merge`, `mergeWith`.
   - Positional pairing: `zip`, `zipWith`.
   - Latest-state recompute: `combineLatest`.
   - Completion dependency only: `then`, `thenMany`.
5. Make terminal behavior explicit.
   - Empty fallback: `defaultIfEmpty`, `switchIfEmpty`.
   - Error recovery: `onErrorReturn`, `onErrorResume`, `onErrorMap`.
   - Cleanup: `doFinally`.
   - Bounded retry: `retry(n)` or `retryWhen(...)` with a deliberate policy.
6. Check demand and metadata.
   - Keep natural demand when the source is already bounded.
   - Add `limitRate(...)` or an explicit overflow policy only when mismatch is real.
   - Use `contextWrite(...)` and `deferContextual(...)` when metadata must survive async boundaries.

## Ordinary workflow

1. State the contract as cardinality, empty behavior, and error behavior.
2. Pick the smallest source factory that still reflects the real boundary.
3. Compose the operator chain in this order: source, business transform, combination, empty behavior, error behavior, cleanup.
4. If one blocking call exists, isolate it once with `Mono.fromCallable(...)` and keep the rest of the chain reactive.
5. Add an ordinary backpressure policy only when the source can outrun downstream.
6. If metadata must cross async boundaries, write it into `Context` and read it where needed.
7. Open a reference only when a named blocker appears.

## Reactor quick reference

| Need | Default move | Why |
| --- | --- | --- |
| one result | `Mono<T>` | preserves 0..1 semantics directly |
| many results | `Flux<T>` | keeps demand and streaming visible |
| lazy one-shot lookup | `Mono.fromCallable(...)` | captures deferred work once per subscription |
| async step returning a publisher | `flatMap(...)` | flattens the nested publisher |
| async step with ordering | `concatMap(...)` | preserves source order |
| synchronous reshape | `map(...)` | keeps the chain simple |
| static empty fallback | `defaultIfEmpty(...)` | replaces empty with one value |
| dynamic empty fallback | `switchIfEmpty(...)` | switches to another publisher |
| error fallback | `onErrorResume(...)` | chooses a replacement publisher |
| exception translation | `onErrorMap(...)` | preserves failure flow while changing the type |
| sequential combination | `concat(...)` | later sources wait for earlier completion |
| concurrent combination | `merge(...)` | emits as soon as values arrive |
| pair by index | `zip(...)` | aligns values positionally |
| latest-state recompute | `combineLatest(...)` | recomputes when any source changes |
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

## Common pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| using `map(...)` for async work | nests publishers or hides concurrency | use `flatMap(...)` or `concatMap(...)` |
| returning `Mono<List<T>>` for a streaming contract | hides streaming semantics and demand | use `Flux<T>` unless the collection itself is the single value |
| placing blocking I/O inside `map(...)` | occupies the current worker thread invisibly | isolate it with `fromCallable(...)` |
| using `retry()` without a bound or policy | can loop forever under outage | use bounded retry or a deliberate `Retry` policy |
| treating `Context` like mutable shared state | writes are immutable and per subscription | write a new `Context` and read it with `deferContextual(...)` |
| choosing `merge(...)` when order matters | output order becomes unstable | use `concat(...)` or `concatMap(...)` |
| using programmatic creation for ordinary values | makes the source harder to reason about | stay with factory methods until a blocker exists |

## Validation checklist

- [ ] `Flux` vs `Mono` matches the real cardinality contract.
- [ ] Source creation reflects the true boundary: eager, lazy, async, future, or one blocking call.
- [ ] Operator choice matches sync vs async work and ordering requirements.
- [ ] Empty behavior, error behavior, and cleanup are explicit.
- [ ] Ordinary backpressure decisions are explicit only when mismatch is real.
- [ ] `Context` carries metadata, not primary payload.
- [ ] Any advanced blocker is routed to exactly one reference.
- [ ] The ordinary path is understandable from this file alone.

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
| you must adapt a blocking or thread-affine API without hiding the cost | [Blocking Bridges](references/blocking-bridges.md) |
| assembly/runtime diagnosis or local signal tracing becomes the main job | [Debugging and Observability](references/debugging-and-observability.md) |

## Output contract

Return:

1. The chosen sequence type and source boundary.
2. The operator chain with ordering, empty, and error decisions.
3. Any demand, blocking, or `Context` decision that changes runtime behavior.
4. Any blocker that requires opening exactly one reference first.
