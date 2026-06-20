---
description: >-
  Overview of the Reactor plugin, its included skills, selection guidance, and reactive programming workflow coverage.
---

# Reactor

Reactor is a shared, skill-first plugin for Project Reactor reactive programming work in the Sinon Claude marketplace.

## Purpose

- Provide reusable Reactor workflows that remain portable across Claude Code plugin installations.
- Keep skills practical, example-driven, and focused on real reactive programming tasks rather than framework trivia.
- Separate reactive programming concerns from Java language, Spring framework, and general concurrency concerns.
- Document the primary skill for each Reactor task while keeping secondary concerns explicit.

## Included Skills

| Skill | Domain | One-line scope |
| --- | --- | --- |
| `reactor-core` | Core types | Flux/Mono selection, source creation, operator composition, combination, error handling, backpressure, Context |
| `reactor-scheduling` | Execution context | Scheduler choice, publishOn/subscribeOn placement, blocking offload, thread-affinity boundaries |
| `reactor-sinks` | Hot sources | Sinks API, ConnectableFlux, replay/multicast choices, emit result handling |
| `reactor-testing` | Test verification | StepVerifier, virtual time, TestPublisher, PublisherProbe, post-verification checks |

## Skill Selection Tree

Use this tree to select the primary skill for any Reactor task.

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

### Selection by Surface Area

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

### Primary Skill and Secondary Concerns

These patterns keep one primary skill while naming the Reactor concern that may need a focused reference from another area.

| Primary task | Secondary concern | Primary skill | Secondary Reactor concern |
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

Reactor-specific reactive programming and operator composition belong in Reactor guidance.
General concurrency patterns outside the Reactor ecosystem belong elsewhere.

WebFlux event-loop safety belongs here only when the task is Reactor operator placement, blocking offload, or scheduler selection inside a pipeline.
WebFlux server configuration, handler setup, and transport wiring belong outside this plugin.

## Runtime Model

This plugin uses one shared plugin root with a thin Claude manifest:

- `.claude-plugin/plugin.json`

Claude Code discovers default plugin-root surfaces automatically, so no component path fields are needed for standard surfaces.
This includes `skills/`, `agents/`, and executable `bin/` when present.

## Plugin Layout

```text
plugins/reactor/
├── .claude-plugin/plugin.json
├── README.md
├── agents/
│   └── reactor-architect.md
└── skills/
    ├── reactor-core/
    ├── reactor-scheduling/
    ├── reactor-sinks/
    └── reactor-testing/
```

## Shipped Surfaces

- The plugin ships four reusable Reactor skills under `skills/`.
- The plugin ships one plugin-root agent: `reactor-architect` for Flux/Mono composition, scheduler, hot-source, and testing workflow decisions.
- The plugin does not ship hooks, MCP servers, LSP servers, or plugin-root custom runtime data surfaces.

## Design Principles

- Prefer working reactive pipeline examples over isolated API documentation.
- Keep examples minimal but runnable in spirit.
- Select the smallest Reactor skill that matches the primary task.
- Keep `SKILL.md` self-contained and usable on its own.
  - Use `references/` only for supplemental decision aids and longer notes.
- Reactor reference files in `references/` are expected to contain concrete additive examples (code, config, command snippets) and must not devolve into prose-only rule summaries.
  - Prose explains the example, the example proves the rule.

## Installation

Install from Sinon:

```sh
claude plugin install reactor@sinon
```

For local development:

```sh
claude --plugin-dir /path/to/sinon/plugins/reactor
```

## Scope Notes

This plugin does not bundle ready-to-run reactive services.
Skill examples are operator, scheduler, sink, and test patterns that must be adapted to the target pipeline's data source, lifecycle, and execution context.
