---
title: Netty
description: Overview of the Netty plugin, its included skills, and high-performance network application workflow coverage.
---

Netty is a shared, skill-first plugin for Netty core API and Reactor Netty reactive application work in the Sinon universal marketplace.

## Purpose

- Provide reusable Netty workflows that remain portable across Claude Code and Codex-style plugin systems.
- Keep skills practical, example-driven, and focused on real network application tasks rather than framework trivia.
- Separate network application concerns from Java language, JDK tooling, and general I/O concerns.

## Included Skills

- `netty`: Core Netty API guidance for bootstrap, channels, pipelines, ByteBuf, codecs, and low-level network programming.
- `reactor-netty`: Reactor Netty guidance for reactive HTTP/TCP/UDP servers and clients with Mono/Flux integration.

## When to Use Which Skill

- Core Netty API, bootstrap configuration, channel lifecycle management, pipeline building, ByteBuf manipulation, and custom codec development belong in the Netty core guidance.
- Reactive HTTP servers/clients, TCP/UDP with reactive streams, Mono/Flux transformation, and Project Reactor integration belong in Reactor Netty guidance.

Typical workflow:

1. Establish the core Netty application structure first.
2. Add reactive behavior when stream processing or backpressure is required.
3. Use Netty core guidance for low-level channel and pipeline concerns.
4. Use Reactor Netty guidance for HTTP/WebSocket with reactive patterns.
5. Keep Java syntax, JVM diagnostics, and general I/O questions in the Java- or platform-level plugin surfaces.

## Scope Boundaries

Netty stays responsible for Netty-specific API, reactive programming with Project Reactor, and network application patterns.

Keep these in other plugins:

- Java syntax, records, sealed types, and general language design.
- JDK tools, JVM diagnostics, and GC analysis.
- General HTTP/WebSocket protocol knowledge.

Netty-specific reactive programming and backpressure handling belong in Netty guidance. General reactive programming outside the Netty ecosystem belongs elsewhere.

## Design Principles

- Prefer working network application examples over isolated API documentation.
- Keep examples minimal but runnable in spirit.
- Route to the smallest Netty skill that matches the task.
- Keep `SKILL.md` self-contained and usable on its own; use `references/` only for supplemental decision aids and longer notes.
- Netty reference files in `references/` are expected to contain concrete additive examples (code, config, command snippets) and must not devolve into prose-only rule summaries; prose explains the example, the example proves the rule.

## Installation

Install from Sinon:

```bash
/plugin install netty@sinon
```

For local development:

```bash
cc --plugin-dir /path/to/sinon/plugins/netty
```