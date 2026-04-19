---
title: "Programmatic Sequence Creation"
description: "Open this when ordinary factory methods are insufficient and you need generate, create, push, or using to model the real source boundary."
---

Open this when ordinary factory methods are no longer enough and the source must be modeled with programmatic emission, per-signal state, or explicit resource lifetime.

## Choose the narrowest programmatic API

| Need | Use | Why |
| --- | --- | --- |
| synchronous state machine with one emission step at a time | `generate(...)` | one `next` per callback |
| callback API that may emit from multiple threads | `create(...)` | `FluxSink` supports multi-signal bridging |
| single-threaded callback bridge | `push(...)` | narrower than `create(...)` |
| tie sequence lifetime to a resource | `using(...)` | makes acquisition and cleanup explicit |

## `generate(...)`

```java
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;

final class GeneratedIds {
    Flux<String> nextIds() {
        AtomicInteger state = new AtomicInteger();
        return Flux.generate(sink -> {
            int current = state.getAndIncrement();
            if (current == 5) {
                sink.complete();
                return;
            }
            sink.next("id-" + current);
        });
    }
}
```

## `create(...)`

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

final class CallbackBridge {
    Flux<String> events() {
        return Flux.create(sink -> {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                sink.next("A");
                sink.next("B");
                sink.complete();
            });
            sink.onDispose(executor::shutdownNow);
        }, FluxSink.OverflowStrategy.BUFFER);
    }
}
```

## `push(...)`

```java
import reactor.core.publisher.Flux;

final class PushBridge {
    Flux<String> singleThreadEvents() {
        return Flux.push(sink -> {
            sink.next("start");
            sink.next("finish");
            sink.complete();
        });
    }
}
```

## `using(...)`

```java
import reactor.core.publisher.Flux;

final class ResourceBoundRead {
    Flux<String> lines() {
        return Flux.using(
            SampleResource::new,
            resource -> Flux.fromArray(resource.lines()),
            SampleResource::close
        );
    }

    static final class SampleResource {
        String[] lines() {
            return new String[] {"a", "b"};
        }

        void close() {
        }
    }
}
```

## Guardrails

- Prefer `push(...)` over `create(...)` only when one producer thread owns all emission.
- Add `onDispose(...)` or `onCancel(...)` whenever the callback source owns resources.
- Do not use programmatic creation to simulate ordinary collections, ranges, or one-shot values.
- Re-check overflow behavior any time you choose `create(...)`.
