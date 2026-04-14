---
title: Virtual Threads Reference
description: >-
  Reference for virtual thread fit, limits, and review questions in Java concurrency work.
---

Use official Java and OpenJDK materials for version-specific behavior and limitations.

## Version-Sensitive Guidance

- Java 21-23: review `synchronized` sections carefully because virtual threads can still pin to carrier threads in those baselines.
- Java 24+: JEP 491 removes `synchronized`-driven pinning as the default concern, but native or JNI-heavy paths can still pin virtual threads.
- Treat `ScopedValue` as version-sensitive rather than a universal default: it is preview on Java 21-24 and finalized in Java 25.

## Concrete Code Examples

Simplest virtual-thread execution model for blocking-task workloads:

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> service.handle(request));
}
```

Scope-bounding virtual threads without a pool:

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Future<String> a = scope.fork(() -> blockingCall("a"));
    Future<String> b = scope.fork(() -> blockingCall("b"));
    scope.join();
    // children inherit parent interruption and cancellation
}
```

ScopedValue for immutable request context (JDK 21-25 preview, finalized JDK 25+):

```java
final class RequestContextHandler {
    static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

    void handle(Request req) {
        ScopedValue.runWhere(REQUEST_ID, req.id(), () -> innerService.process());
    }
}
```

Pinning diagnosis on JDK 21-23 (look for carrier-thread blocking in thread dumps):

```bash
# Thread.dump shows virtual thread in RUNNABLE state while carrier is parked:
# "VirtualThread #123" prio=5 state=RUNNABLE
#   at java.base/java.lang.Continuation.enter()

# Synchronized block that pins the carrier:
synchronized(lock) {   // pins virtual thread to carrier on JDK 21-23
    doWork();
}
```

Avoid broad `ThreadLocal` in virtual-thread workloads; use `ThreadLocal` only for thread-specific caches that tolerate per-task storage:

```java
// Caution: each virtual thread carries its own ThreadLocal copy
static final ThreadLocal<int[]> buffer = ThreadLocal.withInitial(() -> new int[256]);
```

## Review Questions

- Is the workload dominated by blocking I/O?
- Are thread-local assumptions or pinning risks present?
- Is the team already on a Java baseline that supports virtual threads cleanly?
- Is the runtime on Java 21-23 or Java 24+ before discussing `synchronized` pinning?

## Guidance

Virtual threads are strongest when they reduce the cost of many blocking tasks. They are not a substitute for understanding contention, CPU saturation, or poor task boundaries.

- Prefer them for blocking-request or I/O-heavy workloads, not CPU-bound hot loops.
- Audit `ThreadLocal` usage before migrating broad request flows; immutable request context may fit `ScopedValue` better when the baseline supports it cleanly.
- Keep JFR and thread-dump evidence close to the decision so virtual threads are justified by measured blocking rather than novelty.
