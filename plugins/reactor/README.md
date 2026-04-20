---
title: Reactor
description: Overview of the Reactor plugin, its included skills, routing decisions, and reactive programming workflow coverage.
---

Reactor is a shared, skill-first plugin for Project Reactor reactive programming work in the Sinon universal marketplace.

## Purpose

- Provide reusable Reactor workflows that remain portable across Claude Code and Codex-style plugin systems.
- Keep skills practical, example-driven, and focused on real reactive programming tasks rather than framework trivia.
- Separate reactive programming concerns from Java language, Spring framework, and general concurrency concerns.
- Act as an orchestrator that routes every reactive programming task to exactly one skill.

## Included Skills

| Skill | Domain | One-line scope |
| --- | --- | --- |
| `reactor-core` | Core types | Flux/Mono selection, source creation, operator composition, combination, error handling, backpressure, Context |
| `reactor-scheduling` | Execution context | Scheduler choice, publishOn/subscribeOn placement, blocking offload, thread-affinity boundaries |
| `reactor-sinks` | Hot sources | Sinks API, ConnectableFlux, replay/multicast choices, emit result handling |
| `reactor-testing` | Test verification | StepVerifier, virtual time, TestPublisher, PublisherProbe, post-verification checks |

## Routing decision tree

Use this tree to select the correct skill for any Reactor task.

```text
Is the task about testing a publisher?
  YES -> reactor-testing
  NO  -> Continue

Does the task involve manual programmatic emission or hot-source design?
  YES -> reactor-sinks
  NO  -> Continue

Does the task involve scheduler choice, thread placement, or publishOn/subscribeOn?
  YES -> reactor-scheduling
  NO  -> reactor-core (default)
```

### Routing by surface area

| Task keyword or intent | Skill |
| --- | --- |
| Flux, Mono, map, flatMap, concatMap, filter, zip, merge, combineLatest, concat | reactor-core |
| fromCallable, fromSupplier, just, defer, generate, create, push, using | reactor-core |
| onErrorResume, retry, switchIfEmpty, defaultIfEmpty, doFinally | reactor-core |
| Context, contextWrite, deferContextual | reactor-core |
| Schedulers.parallel, Schedulers.boundedElastic, Schedulers.single | reactor-scheduling |
| publishOn, subscribeOn, blocking bridge, thread-affine | reactor-scheduling |
| Sinks.one, Sinks.many, Sinks.empty, tryEmitNext, emitNext | reactor-sinks |
| multicast, replay, unicast, share, autoConnect, refCount, ConnectableFlux | reactor-sinks |
| StepVerifier, TestPublisher, PublisherProbe, withVirtualTime, expectNext | reactor-testing |

### Cross-skill dependencies

These patterns require coordination between two skills:

| Primary task | Secondary concern | Primary skill | Consult also |
| --- | --- | --- | --- |
| Blocking bridge inside a pipeline | Where does the blocking call run? | reactor-core | reactor-scheduling (blocking offload reference) |
| Hot source with thread-safe emission | Which scheduler owns emission? | reactor-sinks | reactor-scheduling (thread-affinity boundary) |
| Testing a pipeline with schedulers | Virtual time vs real scheduler behavior | reactor-testing | reactor-scheduling (virtual time boundary note) |
| Error handling with retry backoff | Retry policy + scheduler interaction | reactor-core | reactor-scheduling (if retry involves scheduling) |
| Context across async boundaries | Context survival through thread hops | reactor-core | reactor-scheduling (context propagation reference) |

## Typical workflow

1. Start with `reactor-core` to define data sources and transformation pipelines.
2. Apply error handling patterns from `reactor-core` for robust pipelines.
3. Use `reactor-scheduling` to control threading and execution context when the default model is insufficient.
4. Use `reactor-sinks` when imperative emission or hot publishing is needed.
5. Validate everything with `reactor-testing` patterns.

## Scope Boundaries

Reactor stays responsible for Project Reactor API, Reactive Streams semantics, and operator patterns.

These topics fall outside Reactor's scope:

- Java syntax, records, sealed types, and general language design.
- Spring WebFlux configuration and server setup.
- Netty or Reactor Netty network programming.
- Kotlin coroutines and Flow.
- R2DBC or Spring Data reactive repository configuration.

Reactor-specific reactive programming and operator composition belong in Reactor guidance. General concurrency patterns outside the Reactor ecosystem belong elsewhere.

## Design Principles

- Prefer working reactive pipeline examples over isolated API documentation.
- Keep examples minimal but runnable in spirit.
- Route to the smallest Reactor skill that matches the task.
- Keep `SKILL.md` self-contained and usable on its own; use `references/` only for supplemental decision aids and longer notes.
- Reactor reference files in `references/` are expected to contain concrete additive examples (code, config, command snippets) and must not devolve into prose-only rule summaries; prose explains the example, the example proves the rule.

## Installation

Install from Sinon:

```bash
/plugin install reactor@sinon
```

For local development:

```bash
cc --plugin-dir /path/to/sinon/plugins/reactor
```
