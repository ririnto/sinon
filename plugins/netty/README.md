---
description: >-
  Overview of the Netty plugin, its included skills, and high-performance network application workflow coverage.
---

# Netty

Netty is a shared, skill-first plugin for Netty core API and Reactor Netty reactive application work in the Sinon Claude marketplace.

## Purpose

- Provide reusable Netty workflows that remain portable across Claude Code plugin installations.
- Keep skills practical, example-driven, and focused on real network application tasks rather than framework trivia.
- Clarify handoffs between low-level Netty APIs and Reactor Netty application patterns.

## Included Skills

- `netty`: Core Netty API guidance for bootstrap, channels, pipelines, ByteBuf, codecs, and low-level network programming.
- `reactor-netty`: Reactor Netty guidance for reactive HTTP/TCP/UDP/QUIC servers and clients with Mono/Flux integration.

## When to Use Which Skill

- Core Netty API, bootstrap configuration, channel lifecycle management, pipeline building,
  ByteBuf manipulation, and custom codec development belong in the Netty core guidance.
- Reactive HTTP servers/clients, TCP/UDP with reactive streams, Mono/Flux transformation,
  and Project Reactor integration belong in Reactor Netty guidance.

Typical workflow:

1. Establish the core Netty application structure first.
2. Add reactive behavior when stream processing or backpressure is required.
3. Use Netty core guidance for low-level channel and pipeline concerns.
4. Use Reactor Netty guidance for HTTP/WebSocket with reactive patterns.
5. When the blocker is not Netty or Reactor Netty behavior, stop at Scope Boundaries before choosing another plugin.

## Scope Boundaries

Netty stays responsible for Netty-specific API, Project Reactor integration through Reactor Netty, and network application patterns.

These topics fall outside Netty's scope:

- Java syntax, records, sealed types, and general language design.
- JDK tools, JVM diagnostics, and GC analysis.
- General HTTP/WebSocket protocol knowledge.

Netty-specific reactive programming and backpressure handling stay in this plugin.
Project Reactor patterns without Netty or Reactor Netty context belong in the Reactor plugin.

## Runtime Model

This plugin uses `.claude-plugin/plugin.json` at the plugin root.

## Plugin Layout

```text
plugins/netty/
+-- .claude-plugin/plugin.json
+-- README.md
+-- skills/
    +-- netty/
    +-- reactor-netty/
```

## Shipped Surfaces

- The plugin ships two reusable networking skills under `skills/`.

## Design Principles

- Prefer working network application examples over isolated API documentation.
- Choose the smallest Netty skill that matches the task.
- References focus on concrete additive examples that support a decision.

## Installation

Install from Sinon:

```sh
claude plugin install netty@sinon
```

For local development:

```sh
claude --plugin-dir /path/to/sinon/plugins/netty
```

## Scope Notes

This plugin does not bundle ready-to-run server templates.
Skill examples are authoring patterns that must be adapted to the target application's transport, protocol, and resource-management constraints.
