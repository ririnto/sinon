---
title: "Context Propagation Across Scheduler Boundaries"
description: "Open this when ThreadLocal-backed data must cross scheduler hops or automatic context propagation becomes the blocker."
---

Open this when execution moves correctly but request metadata, logging context, or security context does not survive the thread change.

## Ordinary rule

Reactor `Context` travels with the subscription, not with the current thread.

## Use `Context` first

```java
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

final class ExplicitContextFlow {
    Mono<String> value() {
        return Mono.deferContextual(context -> Mono.just(context.get("requestId")))
            .publishOn(Schedulers.parallel())
            .contextWrite(Context.of("requestId", "req-7"));
    }
}
```

## Automatic context propagation boundary

Use this only when the Micrometer `context-propagation` library is present and you need `ThreadLocal`-backed integration rather than ordinary Reactor-only `Context` reads.

```java
import reactor.core.publisher.Hooks;

final class AutomaticPropagation {
    void enable() {
        Hooks.enableAutomaticContextPropagation();
    }
}
```

Enable the hook during application startup. It affects only subscriptions created after the hook is enabled.

## Blocker checks

| If the blocker is... | Use |
| --- | --- |
| request metadata in Reactor-only code | explicit `Context` reads and writes |
| `ThreadLocal`-backed library integration | automatic context propagation |
| capturing current thread-local values at subscription time | `contextCapture()` |

## Guardrails

- Do not use automatic propagation as the default answer when explicit `Context` is enough.
- Automatic propagation affects integration boundaries and should stay out of the ordinary path.
- If the real problem is scheduler choice or thread hops themselves, go back to the main scheduling skill.
