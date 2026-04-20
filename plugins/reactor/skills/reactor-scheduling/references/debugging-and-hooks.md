---
title: "Debugging and Hooks"
description: "Open when local log and checkpoint diagnostics are not enough and you need assembly tracing, global hooks, or scheduler-level debugging aids."
---

Open when a few local probes are not enough to explain where execution moved, where assembly happened, or why scheduling behavior differs across chains.

For signal-level inspection (checkpoint, log, doOnEach) without assembly tracing or thread debugging, see [Signal-Level Diagnostics](../../reactor-core/references/debugging-and-observability.md) in the `reactor-core` skill.

## Start with the narrowest advanced tool

| Need | Use | Cost |
| --- | --- | --- |
| capture global assembly traces | `Hooks.onOperatorDebug()` | high |
| instrument runtime with lower steady-state debugging overhead | `ReactorDebugAgent` | medium to high |
| visible thread logging at each stage | `doOnNext` with thread name output | low |

> [!NOTE]
> For `checkpoint("label")` and `log("category")`, see the `reactor-core` skill's signal-level diagnostics reference. This reference covers execution-tracing tools only.

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

`Hooks.onOperatorDebug()` captures assembly stack traces for every operator. Enable once during initialization, not per-pipeline. The cost is high because every operator stores assembly information.

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

Use this pattern to verify that `publishOn(...)` actually switches threads at the expected point. The before/after pair shows whether the scheduler hop occurred.

## `ReactorDebugAgent` for lower-overhead instrumentation

```java
import reactor.core.publisher.Flux;
import reactor.tools.agent.ReactorDebugAgent;

final class DebugAgentExample {
    void instrument() {
        ReactorDebugAgent.init();
    }
    Flux<Integer> values() {
        return Flux.range(1, 3)
            .map(value -> value * 2)
            .checkpoint("after-map");
    }
    void shutdown() {
        ReactorDebugAgent.processErrors();
    }
}
```

`ReactorDebugAgent` provides assembly tracing with lower steady-state overhead than `Hooks.onOperatorDebug()` because it defers processing until explicitly requested. Call `init()` once during initialization and `processErrors()` when you need to dump collected assembly traces. Requires `io.projectreactor:reactor-tools` as a dependency.

## Guardrails

- Prefer named `checkpoint(...)` and local `log(...)` from the core skill's diagnostics before enabling global hooks.
- Remove broad debug instrumentation once the root cause is known.
- `Hooks.onOperatorDebug()` is powerful but expensive because it captures assembly details globally.
- If the real problem is scheduler construction or lifecycle, open [Scheduler Tuning and Custom Schedulers](scheduler-tuning.md).

## When this reference is the wrong tool

- If you need signal-level inspection (what values flow through which operator), see [Signal-Level Diagnostics](../../reactor-core/references/debugging-and-observability.md) in the `reactor-core` skill.
- If the chain is correct but `ThreadLocal`-backed data disappears, open [ThreadLocal Context Bridging](threadlocal-context-bridging.md).
- If the main need is virtual time or scheduler replacement inside tests, use the testing skill instead.
