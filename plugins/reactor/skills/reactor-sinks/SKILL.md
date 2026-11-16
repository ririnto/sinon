---
metadata:
  reference:
    Project Reactor Core:
      version: 3.8.7
      url:
        - https://projectreactor.io/docs/core/release/reference/aboutDoc.html
        - https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Sinks.MulticastSpec.html
        - https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Sinks.MulticastReplaySpec.html
    Project Reactor BOM:
      version: 2025.0.7
      url: https://repo.maven.apache.org/maven2/io/projectreactor/reactor-bom/2025.0.7/reactor-bom-2025.0.7.pom
name: reactor-sinks
description: >-
  Design Reactor manual or hot sources, choose Sinks and replay/multicast policies, and handle emission failures.
---

# Reactor Sinks

## Official Baseline

- Use the official Project Reactor 3.8.x sinks reference for this skill.
  - Reviewed against `reactor-core` 3.8.7 and `reactor-bom` 2025.0.7.
  These versions are documentation baselines, not dependency pins.
- Before adding or upgrading Reactor dependencies, check `io.projectreactor:reactor-bom` and `io.projectreactor:reactor-core` in Maven Central for the latest stable compatible release.
  Honor the project's existing platform, BOM, or pins and keep Reactor modules on the same managed release train.

Author Reactor hot sources deliberately.

## Goal

This skill covers the ordinary path for choosing `Sinks.one()`, `Sinks.empty()`, or `Sinks.many()`, selecting unicast vs multicast vs replay behavior, handling `tryEmit*` / `emit*` outcomes, and deciding when a sink is the right tool instead of ConnectableFlux-style hot conversion.
Keep advanced connection lifecycle, contention tuning, and internal unsafe sinks in blocker references.

## Scope

Activate this skill for:

- creating a programmatic hot source in Reactor
- deciding whether a sink or a shared/replayed cold source is the correct design
- choosing between `Sinks.one()`, `Sinks.empty()`, and `Sinks.many()`
- choosing unicast, multicast, or replay behavior for multiple subscribers
- handling `EmitResult`, `EmitFailureHandler`, or ordinary backpressure outcomes from manual emission
- reviewing why late subscribers miss values or replay old ones

Do not activate for:

- ordinary `Flux` / `Mono` composition as the main problem
- scheduler choice or thread placement as the main problem
- `reactor-test` APIs as the main job
- framework-specific event buses or transport integration details
- legacy `Processor`-based designs

When choosing a sink flavor, retention policy, or hot-source boundary, read [the selection guide](references/sink-selection.md).

## Operating rules

- Use Sinks when you must emit programmatically into a Reactor publisher.
- Prefer `Sinks.one()` for exactly one terminal value, `Sinks.empty()` for terminal-only completion/error, and `Sinks.many()` for multi-value streams.
- Use unicast when there is exactly one subscriber, multicast for live fan-out, and replay when late subscribers must observe history.
  A second unicast subscription receives `IllegalStateException`.
- For multicast, choose `onBackpressureBuffer()` when retention is acceptable, `directAllOrNothing()` when all subscribers must advance together, and `directBestEffort()` when slow subscribers may miss values but fast ones should continue.
- For replay, choose whether all history, the latest signal, or a bounded size/time window should be retained.
- Use `tryEmit*` when the caller can inspect and branch on `EmitResult` immediately.
- Use `emit*` only when retry semantics are deliberate and an `EmitFailureHandler` is part of the design.
- Keep sink backpressure policy explicit at the sink flavor boundary.
- If you only need to share or replay an existing cold source, prefer ConnectableFlux-style operators over a new sink.
- Treat `Sinks.unsafe()` as an internal optimization boundary, not an ordinary default.

## Task Context

Read the producers, subscribers, and tests to establish emission ownership, demand, and late-subscriber requirements.
Prefer sharing an existing cold source when manual emission is not required.
Expose `Sinks.one()` and `Sinks.empty()` through `asMono()`, and `Sinks.many()` through `asFlux()`.
Use the [reference table](#references) for emission failures, connection lifecycle, or concurrent and unsafe emission.

## Sinks quick reference

| Need | Default move | Why |
| --- | --- | --- |
| one async result | `Sinks.one()` | one value or terminal error |
| terminal-only completion/error | `Sinks.empty()` | no payload value |
| one subscriber with buffering | `Sinks.many().unicast().onBackpressureBuffer()` | single-consumer stream |
| live fan-out with retained backlog | `Sinks.many().multicast().onBackpressureBuffer()` | broadcast current signals and buffer by demand |
| live fan-out where all subscribers move together | `Sinks.many().multicast().directAllOrNothing()` | drop for everyone if one subscriber has no demand |
| live fan-out where only slow subscribers fall behind | `Sinks.many().multicast().directBestEffort()` | keep fast subscribers flowing |
| report lack of demand to the producer | `directAllOrNothing()` with `tryEmitNext(...)` | returns `FAIL_OVERFLOW` when any subscriber lacks demand |
| replay all retained history | `Sinks.many().replay().all()` | late subscribers receive full retained history |
| replay only the latest signal | `Sinks.many().replay().latest()` | late subscribers see current state only |
| replay bounded history | `Sinks.many().replay().limit(...)` | retains selected size or time window |
| immediate emission result | `tryEmitNext(...)` | caller handles `EmitResult` directly |
| retry-aware emission | `emitNext(..., handler)` | retries or fails by policy |
| share existing cold source | `publish()`, `replay()`, `refCount(...)`, `autoConnect(...)` | no new sink required |

## Examples

When implementing one, empty, unicast, multicast, replay, or explicit-emission sinks, read [the examples](references/sink-examples.md).

## Common pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| creating a sink to share an existing cold source | adds manual-emission complexity without need | use `publish()`, `replay()`, or `refCount(...)` |
| using multicast when late subscribers need history | late subscribers only see future values | use replay |
| ignoring `EmitResult` from `tryEmit*` | failures become invisible | branch on the result or switch to `emit*` |
| using replay with no limit by default | cached history can grow without bound | choose a size or time limit deliberately |
| assuming safe sinks serialize every producer intention automatically | concurrent calls can still return `FAIL_NON_SERIALIZED` | handle contention or move to a coordinated emission strategy |

## Completion

Verify the changed delivery contract with affected native tests, including demand, late subscribers, cancellation, or concurrent emission when relevant.
Keep retention and emission-failure policy explicit without requiring every sink variant in each test suite.

## References

| Open this when... | Reference |
| --- | --- |
| `EmitResult`, retry policy, overflow, or terminated/cancelled emission is the blocker | [Sink Emission Failures](references/failures.md) |
| the real problem is connect, disconnect, replay, or subscriber rendezvous for a shared cold source | [Connectable Flux Patterns](references/connectable-patterns.md) |
| contention, external synchronization, or `Sinks.unsafe()` is the blocker | [Concurrent and Unsafe Emission](references/concurrent-and-unsafe-emission.md) |

## Result

Complete the authorized change and explain the sink or sharing choice, subscriber model, and emission policy.
Report the checks run and any unverified retention, lifecycle, or concurrency boundary.
