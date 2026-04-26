---
title: "Scheduler Tuning and Custom Schedulers"
description: "Open this when shared scheduler defaults are insufficient and you must create, tune, observe, or dispose schedulers deliberately."
---

Open this when the blocker is no longer scheduler choice but scheduler construction, capacity, lifecycle, or observability.

## Replace shared defaults only when the whole application needs it

```java
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import java.util.concurrent.ThreadFactory;
final class ReplaceSharedSchedulers {
    void installFactory() {
        Schedulers.setFactory(new Schedulers.Factory() {
            @Override
            public Scheduler newParallel(int parallelism, ThreadFactory threadFactory) {
                return Schedulers.Factory.DEFAULT.newParallel(parallelism, threadFactory);
            }
            @Override
            public Scheduler newSingle(ThreadFactory threadFactory) {
                return Schedulers.Factory.DEFAULT.newSingle(threadFactory);
            }
            @Override
            public Scheduler newBoundedElastic(int threadCap, int queuedTaskCap, ThreadFactory threadFactory, int ttlSeconds) {
                return Schedulers.Factory.DEFAULT.newBoundedElastic(threadCap, queuedTaskCap, threadFactory, ttlSeconds);
            }
        });
    }
    void resetFactory() {
        Schedulers.resetFactory();
    }
}
```

Use shared-scheduler replacement only when scheduler policy must change application-wide. For one bounded use case, prefer a dedicated scheduler instance instead.

## Default vs custom scheduler decision

| Need | Use | Why |
| --- | --- | --- |
| ordinary CPU work | `Schedulers.parallel()` | shared fixed pool is usually enough |
| ordinary blocking bridge | `Schedulers.boundedElastic()` | shared bounded worker growth |
| dedicated named lane | `Schedulers.newSingle(...)` | isolated serialized execution |
| dedicated CPU pool | `Schedulers.newParallel(...)` | explicit thread count and naming |
| dedicated blocking pool | `Schedulers.newBoundedElastic(...)` | explicit cap and queue limit |
| integrate an existing executor | `Schedulers.fromExecutorService(...)` | bridge existing infrastructure deliberately |

> [!NOTE]
> `Schedulers.fromExecutor(Executor)` remains available in Reactor 3.7.18, but Reactor's scheduler guide discourages it when an `ExecutorService` is available. Prefer `Schedulers.fromExecutorService(ExecutorService)` for clearer lifecycle management and disposal semantics.

## Dedicated scheduler factory methods

```java
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
final class CustomSchedulerFactory {
    Scheduler cpuScheduler() {
        return Schedulers.newParallel("reactor-cpu", 4);
    }
    Scheduler blockingScheduler() {
        return Schedulers.newBoundedElastic(32, 1000, "reactor-io");
    }
}
```

## Executor bridge

```java
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
final class ExecutorBridge {
    Scheduler customScheduler() {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
        return Schedulers.fromExecutorService(executor);
    }
}
```

## Shared `boundedElastic()` on virtual threads

On Java 21+, the shared `Schedulers.boundedElastic()` can use a thread-per-task implementation backed by virtual threads when the JVM starts with the Reactor system property set to `true`:

```bash
java -Dreactor.schedulers.defaultBoundedElasticOnVirtualThreads=true -jar app.jar
```

Use this shared path when the whole application should run bounded-elastic tasks on virtual threads. Keep the normal `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` bridge shape; only the scheduler backing changes.

## Lifecycle and disposal

```java
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
final class SchedulerLifecycle {
    void run() {
        Scheduler scheduler = Schedulers.newSingle("reactor-worker");
        try {
            Flux.range(1, 3)
                .publishOn(scheduler)
                .map(value -> value * 2)
                .blockLast();
        } finally {
            scheduler.dispose();
        }
    }
}
```

## Graceful shutdown with `disposeGracefully(...)`

For application shutdown scenarios where in-flight tasks should complete before the scheduler stops, use `disposeGracefully(...)` instead of `dispose()`.

```java
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
final class GracefulShutdown {
    Mono<Void> shutdown(Scheduler scheduler) {
        return Flux.range(1, 3)
            .publishOn(scheduler)
            .map(value -> value * 2)
            .then(scheduler.disposeGracefully());
    }
}
```

`disposeGracefully()` returns a `Mono<Void>` that completes when all scheduled tasks have finished. Use it in application lifecycle hooks where graceful degradation is required. `dispose()` is immediate and drops in-flight work.

## Tuning guardrails

- Shared schedulers are the default. Create dedicated schedulers only when isolation is a real requirement.
- Replace shared defaults with `Schedulers.setFactory(...)` only when the policy must change globally.
- Tune `newBoundedElastic(...)` only when thread cap or queue cap is the actual blocker.
- Prefer naming dedicated schedulers so thread dumps and logs are readable.
- If the application is on Java 21+, virtual-thread-backed bounded elastic is enabled by `reactor.schedulers.defaultBoundedElasticOnVirtualThreads=true`, not by changing each call site.

## Observability boundary

- Scheduler-level metrics and decorated executor services belong here, not in the ordinary path.
- If the real problem is signal tracing rather than scheduler construction, open [Debugging and Hooks](debugging-and-hooks.md).
