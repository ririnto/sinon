---
title: "Hot, Cold, and Multicasting"
description: "Open this when shared subscriptions, replay, or late-subscriber behavior changes the design and you need to reason about cold versus hot Reactor sources."
---

Open this when identical subscriptions must not repeat the same work or when late subscribers must see shared or replayed signals.

Keep this reference at the boundary-recognition level. If the real work is choosing concrete `publish()`, `replay()`, `autoConnect(...)`, or `refCount(...)` patterns for a shared cold source, open the sinks skill's connectable-patterns reference instead.

## Core distinctions

| Need | Use | Notes |
| --- | --- | --- |
| re-run the source per subscriber | cold source | ordinary Reactor default |
| share one live subscription | `share()` or `publish().refCount(...)` | subscribers join an active source |
| delay connection until enough subscribers arrive | `autoConnect(n)` | useful for coordinated consumers |
| replay earlier signals to late subscribers | `replay(...)` | trades memory for history |

## Design checks

- `Flux.just(...)` captures values eagerly at assembly time, but each subscriber still receives the stored values.
- `defer(...)` keeps source creation cold and subscription-specific.
- `share()` is the shortest move when one live subscription is enough and replay is not needed.
- If the design requires manual emission semantics rather than shared subscription semantics, treat it as a sink problem.
- If you need concrete ConnectableFlux lifecycle recipes, use the sinks skill's dedicated reference for those patterns.

## Failure checks

- If subscribers must each re-run the source, keep it cold.
- If backpressure behavior changes after `publish()` or `share()`, inspect shared downstream demand rather than only the original source.
- If you need explicit manual emission APIs or replay buffers by policy, handle that as a separate hot-source design problem.
