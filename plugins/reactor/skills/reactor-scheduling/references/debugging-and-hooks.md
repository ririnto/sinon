---
title: "Debugging and Hooks"
description: "Open this when local log and checkpoint diagnostics are not enough and you need assembly tracing, global hooks, or scheduler-level debugging aids."
---

Open this when a few local probes are not enough to explain where execution moved, where assembly happened, or why scheduling behavior differs across chains.

## Start with the narrowest advanced tool

| Need | Use | Cost |
| --- | --- | --- |
| mark one suspicious stage | `checkpoint("label")` | low |
| trace local signals and requests | `log("category")` | low to medium |
| capture global assembly traces | `Hooks.onOperatorDebug()` | high |
| instrument runtime with lower steady-state debugging overhead | `ReactorDebugAgent` | medium to high |

## Global assembly tracing

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;

final class OperatorDebugExample {
    Flux<Integer> values() {
        Hooks.onOperatorDebug();
        return Flux.range(1, 3)
            .map(value -> value * 2)
            .checkpoint("after-map");
    }
}
```

## Visible thread logging

```java
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

final class ThreadLoggingExample {
    Flux<Integer> values() {
        return Flux.range(1, 3)
            .doOnNext(value -> currentThread("before", value))
            .publishOn(Schedulers.parallel())
            .doOnNext(value -> currentThread("after", value));
    }

    private void currentThread(String stage, Integer value) {
        System.out.println(stage + ":" + value + ":" + Thread.currentThread().getName());
    }
}
```

## Guardrails

- Prefer named `checkpoint(...)` and local `log(...)` before enabling global hooks.
- Remove broad debug instrumentation once the root cause is known.
- `Hooks.onOperatorDebug()` is powerful but expensive because it captures assembly details globally.
- If the real problem is scheduler construction or lifecycle, open [Scheduler Tuning and Custom Schedulers](scheduler-tuning.md).

## When this reference is the wrong tool

- If the chain is correct but `ThreadLocal`-backed data disappears, open [Context Propagation Across Scheduler Boundaries](context-propagation.md).
- If the main need is virtual time or scheduler replacement inside tests, use the testing skill instead.
