---
title: Virtual Threads Reference
description: >-
  Reference for virtual thread fit assessment, version-sensitive limits,
  ScopedValue usage, pinning diagnosis, and ThreadLocal guidance
  in Java concurrency work.
---

Open this reference when the workload shape is already classified as blocking-I/O and you still need one of these deeper jobs:

- evaluate whether virtual threads are the right fit for a specific concurrency model
- understand version-specific pinning behavior on JDK 21-23 vs 24+
- use `ScopedValue` for immutable request context instead of broad `ThreadLocal`
- diagnose pinning from thread dumps or JFR traces
- audit `ThreadLocal` usage before migrating to virtual threads

Use official Java and OpenJDK materials for version-specific behavior and limitations.

## Version-sensitive guidance

- Java 21+: `Thread.ofVirtual()` and `Executors.newVirtualThreadPerTaskExecutor()` are stable virtual-thread APIs.
- Java 21-23: review `synchronized` sections carefully because virtual threads can still pin to carrier threads in those baselines.
- Java 24+: JEP 491 removes `synchronized`-driven pinning as the default concern, but native or JNI-heavy paths can still pin virtual threads.
- Treat `ScopedValue` as version-sensitive rather than a universal default: it is preview on Java 21-24 and finalized in Java 25.

## Concrete code examples

Simplest virtual-thread execution model for blocking-task workloads:

```java
import java.util.concurrent.Executors;

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> service.handle(request));
}
```

Scope-bounding virtual threads without a pool (Structured Concurrency API, preview through JDK 25 inclusive; JEP 505 is the fifth preview):

```java
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<String> a = scope.fork(() -> blockingCall("a"));
    Subtask<String> b = scope.fork(() -> blockingCall("b"));
    scope.join();
    scope.throwIfFailed();
    String first = a.get();
    String second = b.get();
}
```

> [!IMPORTANT]
> `StructuredTaskScope` is a preview API through JDK 25 (`JEP 505: Structured Concurrency (Fifth Preview)`). Compile with `--enable-preview --release <n>` and accept that the API surface MAY still move before finalization. Call `scope.fork()` which returns `StructuredTaskScope.Subtask<T>`; read values only after `join()` (and `throwIfFailed()` when using `ShutdownOnFailure`) has returned.

ScopedValue for immutable request context (preview on JDK 21-24, finalized in JDK 25):

```java
final class RequestContextHandler {
    static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

    void handle(Request req) {
        ScopedValue.runWhere(REQUEST_ID, req.id(), () -> innerService.process());
    }
}
```

Pinning diagnosis on JDK 21-23: look for carrier-thread blocking in thread dumps.

```text
"VirtualThread #123" prio=5 state=RUNNABLE
  at java.base/java.lang.Continuation.enter()
```

On JDK 21-23, a synchronized block can pin the virtual thread to its carrier while the monitor is held:

```java
synchronized (lock) {
    doWork();
}
```

Avoid broad `ThreadLocal` in virtual-thread workloads; use `ThreadLocal` only for thread-specific caches that tolerate per-task storage:

```java
static final ThreadLocal<int[]> buffer = ThreadLocal.withInitial(() -> new int[256]);
```

Caution: each virtual thread carries its own `ThreadLocal` copy, so broad request context or large per-thread buffers can multiply memory use.

## Review questions

- Is the workload dominated by blocking I/O?
- Are thread-local assumptions or pinning risks present?
- Is the team already on a Java baseline that supports virtual threads cleanly?
- Is the runtime on Java 21-23 or Java 24+ before discussing `synchronized` pinning?

## Guidance

Virtual threads are strongest when they reduce the cost of many blocking tasks. They are not a substitute for understanding contention, CPU saturation, or poor task boundaries.

- Prefer them for blocking-request or I/O-heavy workloads, not CPU-bound hot loops.
- Audit `ThreadLocal` usage before migrating broad request flows; immutable request context may fit `ScopedValue` better when the baseline supports it cleanly.
- Keep JFR and thread-dump evidence close to the decision so virtual threads are justified by measured blocking rather than novelty.
