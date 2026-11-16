---
metadata:
  reference:
    Project Reactor Core:
      version: 3.8.7
      url:
        - https://projectreactor.io/docs/core/release/reference/aboutDoc.html
        - https://projectreactor.io/docs/core/release/reference/coreFeatures/schedulers.html
        - https://projectreactor.io/docs/core/release/api/reactor/core/scheduler/Scheduler.html
    Project Reactor BOM:
      version: 2025.0.7
      url: https://repo.maven.apache.org/maven2/io/projectreactor/reactor-bom/2025.0.7/reactor-bom-2025.0.7.pom
name: reactor-scheduling
description: >-
  Choose Reactor schedulers, place publishOn/subscribeOn, offload blocking work, or diagnose thread hops.
---

# Reactor Scheduling

## Official Baseline

- Use the official Project Reactor 3.8.x scheduler reference for this skill.
  - Reviewed against `reactor-core` 3.8.7 and `reactor-bom` 2025.0.7.
  These versions are documentation baselines, not dependency pins.
- Before adding or upgrading Reactor dependencies, check `io.projectreactor:reactor-bom` and `io.projectreactor:reactor-core` in Maven Central for the latest stable compatible release.
  Honor the project's existing platform, BOM, or pins and keep Reactor modules on the same managed release train.

Choose execution context deliberately in Reactor.

## Goal

This skill covers the ordinary path for scheduler choice, `publishOn(...)` vs `subscribeOn(...)`, blocking offload, thread-affinity boundaries, and local scheduling diagnostics.
Keep custom scheduler factories, automatic context propagation, global hooks, and test-only virtual-time work in blocker references or dedicated Reactor test guidance.

## Scope

Activate this skill for:

- deciding where a Reactor chain should run
- choosing among `Schedulers.parallel()`, `boundedElastic()`, `single()`, or `immediate()`
- deciding whether `publishOn(...)` or `subscribeOn(...)` is the right move
- isolating one blocking boundary without smearing thread switches across the chain
- checking whether ThreadLocal assumptions break once the pipeline becomes asynchronous
- diagnosing thread hops or execution placement with local Reactor tools

Do not activate for:

- `Flux` / `Mono` source creation and ordinary operator composition as the main problem
- sink design, manual emission APIs, or replay/multicast policy
- `reactor-test` design as the main job
- framework-specific thread models such as Spring, Netty, or messaging runtimes
- custom operator authoring

When choosing a scheduling boundary or diagnosing a thread hop, read [the selection guide](references/scheduling-coverage.md).

## Operating rules

- Reactor is concurrency-agnostic until you introduce a `Scheduler`.
- The source and upstream chain run on the thread that performs `subscribe()` unless you move them.
- Use `publishOn(...)` to switch downstream execution from that point onward.
- Use `subscribeOn(...)` to move subscription and upstream source work.
- Place `subscribeOn(...)` at the source boundary, especially for blocking bridges.
- Use `Schedulers.parallel()` for fast non-blocking CPU work.
- Use `Schedulers.boundedElastic()` for blocking I/O or thread-affine imperative code.
- Treat `Context` as subscription metadata, not as a ThreadLocal replacement by itself.
- Prefer the fewest scheduler hops that preserve correctness.

## Task Context

Read the affected source, scheduler placement, and available execution evidence before adding a thread hop.
Identify which work must move and whether it is CPU-bound, blocking, or thread-affine.
Use the [reference table](#references) for custom capacity, ThreadLocal bridging, or global diagnostics when needed.
Shared scheduler or global-hook changes require authority covering all affected consumers.

## Scheduler quick reference

| Need | Default move | Why |
| --- | --- | --- |
| CPU-bound non-blocking work | `Schedulers.parallel()` | fixed worker pool sized for CPU work |
| blocking I/O or legacy bridge | `Schedulers.boundedElastic()` | bounded worker expansion for blocking tasks |
| one serialized execution lane | `Schedulers.single()` | preserves one-thread affinity |
| no real handoff (test or caller-thread only) | `Schedulers.immediate()` | runs on the current thread, so avoid it in production pipelines |
| move source and subscription | `subscribeOn(...)` | affects upstream work |
| move downstream operators | `publishOn(...)` | affects work after that operator |
| request metadata across async boundaries | `contextWrite(...)` + `deferContextual(...)` | survives thread switches without ThreadLocal assumptions |

## Examples

When placing source offload, downstream handoffs, or request context across threads, read [the examples](references/scheduling-examples.md).

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
| placing `contextWrite(...)` before the operator that reads the context | `contextWrite` affects upstream operators, so the reader cannot see a write placed before it | place `contextWrite` downstream of the reader, as shown in the linked context example |

## Completion

For implementation work, verify affected thread placement, blocking isolation, and context behavior with existing native tests or bounded diagnostics.
Virtual-time tests do not prove real thread affinity.
Use existing execution evidence when sufficient, and add global instrumentation only when the remaining uncertainty requires it.

## References

| Open this when... | Reference |
| --- | --- |
| shared scheduler defaults are not enough and you must create, tune, or replace schedulers explicitly | [Scheduler Tuning and Custom Schedulers](references/scheduler-tuning.md) |
| `ThreadLocal`-backed data must cross scheduler boundaries or automatic context propagation becomes the blocker | [ThreadLocal Context Bridging](references/threadlocal-context-bridging.md) |
| local `log(...)` and `checkpoint(...)` are not enough and you need global debugging hooks or assembly tracing | [Debugging and Hooks](references/debugging-and-hooks.md) |

## Result

Complete the authorized change and explain the workload, scheduler placement, and any ownership or context consequence.
Report the checks run and any unverified capacity or runtime behavior.
