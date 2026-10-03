---
description: >-
  Overview of the Netty plugin, its included skills, and high-performance network application workflow coverage.
metadata:
  reference:
    Claude Code plugins:
      url: https://code.claude.com/docs/en/plugins/manifest-reference.md
    OpenAI Codex plugins:
      url: https://developers.openai.com/plugins/build/plugins.md
---

# Netty

Netty provides skills for core Netty APIs and Reactor Netty clients and servers.

## Included Skills

- `netty`: Core Netty API guidance for bootstrap, channels, pipelines, ByteBuf, codecs, and low-level network programming.
- `reactor-netty`: Reactor Netty guidance for reactive HTTP/TCP/UDP/QUIC servers and clients with Mono/Flux integration.

## When to Use Which Skill

- Core Netty API, bootstrap configuration, channel lifecycle management, pipeline building,
  ByteBuf manipulation, and custom codec development belong in the Netty core guidance.
- Reactive HTTP servers/clients, TCP/UDP with reactive streams, Mono/Flux transformation,
  and Project Reactor integration belong in Reactor Netty guidance.

Select the skill for the API under change, not a sequence from core Netty to reactive transport.
Use low-level guidance only when channel, pipeline, codec, or buffer ownership behavior requires it.
Each skill owns its resource rules and task-specific verification.

## Scope Boundaries

Netty stays responsible for Netty-specific API, Project Reactor integration through Reactor Netty, and network application patterns.

These topics fall outside Netty's scope:

- Java syntax, records, sealed types, and general language design.
- JDK tools, JVM diagnostics, and GC analysis.
- General HTTP/WebSocket protocol knowledge.

Netty-specific reactive programming and backpressure handling stay in this plugin.
Project Reactor patterns without Netty or Reactor Netty context are outside this plugin.

## Runtime Model

Claude Code loads this package through the Sinon marketplace entry and its default component paths.
Codex uses `.codex-plugin/plugin.json` to load skills from the default `skills/` directory.

## Plugin Layout

```text
plugins/netty/
+-- .codex-plugin/plugin.json
+-- README.md
+-- skills/
    +-- netty/
    +-- reactor-netty/
```

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
