---
description: >-
  Overview of the Reactor plugin, its included skills, selection guidance, and reactive programming workflow coverage.
metadata:
  reference:
    Claude Code plugins:
      url: https://code.claude.com/docs/en/plugins/manifest-reference.md
    OpenAI Codex plugins:
      url: https://developers.openai.com/plugins/build/plugins.md
---

# Reactor

Reactor provides skills for Project Reactor composition, scheduling, hot sources, and publisher testing.

## Included Skills

| Skill | Domain | One-line scope |
| --- | --- | --- |
| `reactor-core` | Core types | Flux/Mono selection, source creation, operator composition, combination, error handling, backpressure, Context |
| `reactor-scheduling` | Execution context | Scheduler choice, publishOn/subscribeOn placement, blocking offload, thread-affinity boundaries |
| `reactor-sinks` | Hot sources | Sinks API, ConnectableFlux, replay/multicast choices, emit result handling |
| `reactor-testing` | Test verification | StepVerifier, virtual time, TestPublisher, PublisherProbe, post-verification checks |

## Included Agents

- `reactor-architect`: Flux/Mono composition, scheduler, hot-source, and testing workflow decisions.

`reactor-architect` is a read-only leaf domain router for cross-skill Reactor decisions.
It may load Reactor skills but does not delegate to other agents.

## Skill Selection

### Primary Skill and Secondary Concerns

These patterns keep one primary skill while naming the Reactor concern that may need a focused reference from another area.

| Primary task | Secondary concern | Primary skill | Secondary Reactor concern |
| --- | --- | --- | --- |
| Blocking bridge inside a pipeline | Where does the blocking call run? | reactor-core | reactor-scheduling (blocking offload reference) |
| Hot source with thread-safe emission | Which scheduler owns emission? | reactor-sinks | reactor-scheduling (thread-affinity boundary) |
| Testing a pipeline with schedulers | Virtual time vs real scheduler behavior | reactor-testing | reactor-scheduling (virtual time boundary note) |
| Error handling with retry backoff | Retry policy + scheduler interaction | reactor-core | reactor-scheduling (if retry involves scheduling) |
| Context across async boundaries | Context survival through thread hops | reactor-core | reactor-scheduling (context propagation reference) |

## Applying the Skills

Select the primary skill for the current decision and load secondary guidance only when it changes the answer.
Composition, scheduling, hot sources, and testing are not mandatory sequential stages.
Use existing native tests to prove changed behavior and acceptance criteria, not every Reactor pattern.

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

Claude Code loads this package through the Sinon marketplace entry and its default component paths.
Codex uses `.codex-plugin/plugin.json` to load skills from the default `skills/` directory.
Claude Code also loads the plugin-root `reactor-architect` agent.
The listed plugin-root agent remains a Claude Code surface.

## Plugin Layout

```text
plugins/reactor/
+-- .codex-plugin/plugin.json
+-- README.md
+-- agents/
|   +-- reactor-architect.md
+-- skills/
    +-- reactor-core/
    +-- reactor-scheduling/
    +-- reactor-sinks/
    +-- reactor-testing/
```

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
