---
title: "Context for Library-Facing Flows"
description: "Open this when ordinary Context usage is not enough and you need nested-write rules, precedence behavior, or Context-sensitive composition in library-facing code."
---

Open this when the blocker is no longer 'how do I carry request metadata' but 'how does `Context` behave across composition boundaries'.

## Rules that matter

- `Context` is immutable and attached to a subscription.
- `contextWrite(...)` affects operators upstream from where it appears.
- `deferContextual(...)` reads from the current subscription context.
- `transformDeferredContextual(...)` is the right tool when a mid-chain transformation needs the current `ContextView`.
- Multiple writes to the same key are resolved by the write closest to the reading operator.

## Write placement

```java
import reactor.core.publisher.Mono;

final class WritePlacement {
    Mono<String> correct() {
        return Mono.deferContextual(context -> Mono.just(context.get("requestId")))
            .contextWrite(context -> context.put("requestId", "req-1"));
    }

    Mono<String> nested() {
        return Mono.just("value")
            .flatMap(value -> Mono.deferContextual(context -> Mono.just(context.get("requestId") + ":" + value)))
            .contextWrite(context -> context.put("requestId", "req-1"));
    }
}
```

## Context-sensitive composition

```java
import reactor.core.publisher.Mono;

final class ContextAwareOperator {
    Mono<String> audit(String value) {
        return Mono.just(value)
            .transformDeferredContextual((mono, contextView) -> mono.map(current -> contextView.get("tenant") + ":" + current));
    }
}
```

## Read helpers

| Need | Use |
| --- | --- |
| check presence | `hasKey(...)` |
| get with fallback | `getOrDefault(...)` |
| get as optional | `getOrEmpty(...)` |
| add one key | `put(...)` |
| merge another context | `putAll(...)` |

## Capturing current thread-local values at subscription time

Use `contextCapture()` when you need to snapshot thread-local state (such as MDC, security context) into the Reactor `Context` at subscription time rather than manually writing each key.

```java
import reactor.core.publisher.Mono;

final class ContextCaptureExample {
    Mono<String> withCapturedContext() {
        return Mono.just("value")
            .contextCapture();
    }
}
```

`contextCapture()` snapshots available `ThreadLocal` values (such as MDC or security context) into the Reactor `Context` at subscription time. This requires the Micrometer `context-propagation` library on the classpath and `Hooks.enableAutomaticContextPropagation()` to be enabled. For explicit manual Context writes, prefer `contextWrite(...)` and `deferContextual(...)`.

## Failure checks

- Do not use `Context` as a mutable request cache.
- Do not move business payload into `Context` to avoid changing method signatures.
- Do not assume `ThreadLocal` semantics across scheduler switches.
- If data disappears, inspect write placement before changing read logic.
