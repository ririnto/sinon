---
title: Reactor
description: Overview of the Reactor plugin, its included skills, and reactive programming workflow coverage.
---

Reactor is a shared, skill-first plugin for Project Reactor reactive programming work in the Sinon universal marketplace.

## Purpose

- Provide reusable Reactor workflows that remain portable across Claude Code and Codex-style plugin systems.
- Keep skills practical, example-driven, and focused on real reactive programming tasks rather than framework trivia.
- Separate reactive programming concerns from Java language, Spring framework, and general concurrency concerns.

## Included Skills

- `reactor-core`: Core Reactor types (Flux, Mono), operator composition, data transformation, error handling, and Context propagation.
- `reactor-scheduling`: Scheduler configuration, publishOn/subscribeOn threading, and debugging reactive chains.
- `reactor-sinks`: Sinks API, ConnectableFlux, hot versus cold publishers, and backpressure strategies for manual emission.
- `reactor-testing`: StepVerifier, TestPublisher, PublisherProbe, and virtual time testing with reactor-test.

## When to Use Which Skill

- Flux/Mono creation, operators (map, flatMap, filter), combining (concat, merge, zip), and error handling belong in reactor-core.
- Scheduler selection, publishOn/subscribeOn, threading context switching, and debugging belong in reactor-scheduling.
- Sinks (one, many, empty), ConnectableFlux, caching, replay, and hot publishers belong in reactor-sinks.
- StepVerifier, TestPublisher, virtual time, and testing reactive pipelines belong in reactor-testing.

Typical workflow:

1. Start with reactor-core to define data sources and transformation pipelines.
2. Apply error handling patterns from reactor-core for robust pipelines.
3. Use reactor-scheduling to control threading and execution context.
4. Use reactor-sinks when imperative emission or hot publishing is needed.
5. Validate everything with reactor-testing patterns.

## Scope Boundaries

Reactor stays responsible for Project Reactor API, reactive stream semantics, and operator patterns.

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
