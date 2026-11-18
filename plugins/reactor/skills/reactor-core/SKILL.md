---
metadata:
  reference:
    Project Reactor Core:
      version: 3.8.7
      url:
        - https://projectreactor.io/docs/core/release/reference/aboutDoc.html
        - https://projectreactor.io/docs/core/release/reference/gettingStarted.html
        - https://projectreactor.io/docs/core/release/api/reactor/core/publisher/SynchronousSink.html
        - https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html
        - https://projectreactor.io/docs/core/release/reference/debugging.html
        - https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html
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

Use `Flux` and `Mono` for ordinary Reactor work.

## Goal

Choose Reactor sources, operators, empty/error behavior, demand, and request-scoped `Context`.
Keep scheduler design, sinks, and tests in their own domains.

## Scope

Activate this skill for:

- choosing between `Flux<T>` and `Mono<T>`
- creating a sequence from values, collections, a lazy value, a future, or blocking work
- composing transformations, filtering, sequencing, combination, cancellation-aware fan-out, timeouts, and collection operators
- making empty, error, fallback, cleanup, repeat, retry, and timeout behavior explicit
- deciding whether backpressure defaults suffice or need an overflow policy
- carrying tracing, request, tenant, or auth metadata in `Context`

Do not activate for:

- scheduler selection as the main problem
- manual emission design with `Sinks`
- test-only work centered on `StepVerifier`, `TestPublisher`, or virtual time
- transport, framework, persistence, or web-stack integration details
- custom operator implementation or hook-heavy global instrumentation

When selecting source factories, composition, demand, or diagnostics operators, read [the selection guide](references/operator-selection.md).

## Operating rules

- Model cardinality first: `Mono<T>` for 0..1, `Flux<T>` for 0..N.
- Prefer the narrowest source factory that matches the real data boundary.
- Use `map(...)` for synchronous value-to-value work and `flatMap(...)` for publisher-returning work.
- Use `concatMap(...)` when downstream order must stay stable.
- Use `switchMap(...)` when previous inner publishers should cancel on new trigger (search-as-you-type, latest-win semantics).
- Use `timeout(...)` to bound the wait for any signal and fall back or error on delay.
- For finite `repeat` work, bound re-subscriptions with `repeat(n)` or a terminating `repeatWhen(...)`; value-based stopping cannot stop empty completions.
  For long-lived work, define cancellation and lifecycle ownership and pace re-subscriptions when needed.
- `handle` emits at most one value per input; use `flatMap` or `flatMapIterable` for multiple values.
- Make empty and error behavior explicit before returning the publisher.
- Wrap blocking work once with `Mono.fromCallable(...)` and move it off the caller thread deliberately.
- Use `Context` for cross-cutting metadata, not for primary business payload.
- Treat advanced creation, sinks, scheduling, and testing as separate blockers.

## Task Context

Read the source, pipeline, subscribers, and tests for cardinality, ordering, and terminal behavior.
Change only the operators needed for the requested contract.
Use the [references](#references) for demand, creation, retry, sharing, batching, blocking, or Context.
Operator order follows signal and subscription semantics.

## Examples

For source factories, ordinary operator composition, blocking bridges, or request metadata, read [the examples](references/operator-examples.md).

## Common pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| using `map(...)` for async work | nests publishers | use `flatMap(...)` or `concatMap(...)` |
| returning `Mono<List<T>>` for a streaming contract | hides streaming semantics and demand | use `Flux<T>` unless the collection itself is the single value |
| blocking I/O inside `map(...)` | occupies the worker thread | isolate with `fromCallable(...)` |
| unbounded `retry()` | can loop forever | use bounded retry or a deliberate `Retry` policy |
| repeating without a lifecycle or pacing policy | can re-subscribe indefinitely or overload the source | bound finite work, or manage cancellation and pace a long-lived stream |
| using `timeout(...)` without fallback | the bounded timeout emits a raw `TimeoutException` downstream | handle that error or provide a fallback via `timeout(duration, fallback)` |
| treating `Context` as mutable shared state | writes are immutable and per subscription | write a new `Context` and read with `deferContextual(...)` |
| `merge(...)` when order matters | output order is unstable | use `concat(...)` or `concatMap(...)` |
| programmatic creation for ordinary values | obscures the source | prefer factory methods |
| using `switchMap(...)` when all inner results matter | cancels previous inners before they complete | use `flatMap(...)` or `concatMap(...)` instead |

## Completion

Verify the changed publisher contract with affected native tests and reuse existing coverage when sufficient.
Review the relevant cardinality, ordering, empty/error, cancellation, cleanup, demand, and Context behavior.
Do not require every operator pattern or exactly one reference for each task.

Complete the authorized change and explain the source, operator, and runtime decisions that affect its contract.
Report checks and unresolved demand, lifecycle, or integration boundaries.

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
