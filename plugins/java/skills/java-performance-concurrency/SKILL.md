---
name: java-performance-concurrency
description: >-
  This skill should be used when the user asks to "optimize Java performance", "analyze concurrency design", "use virtual threads", "reduce allocation pressure", "profile Java code", or needs guidance on Java performance and concurrency tradeoffs.
---

# Java Performance Concurrency

## Overview

Use this skill to review Java performance and concurrency decisions with emphasis on evidence, workload shape, and modern JVM capabilities. The common case is not heroic optimization; it is identifying whether the bottleneck is CPU, blocking I/O, contention, or allocation churn, then making the smallest measured change that fits that shape. Prefer profiling and observable workload evidence over intuition.

## Use This Skill When

- You are investigating Java latency or throughput problems.
- You are evaluating lock contention, allocation pressure, or virtual-thread fit.
- You need version-aware guidance for virtual-thread limits on different Java baselines.
- You need a default profiling or concurrency-review workflow before optimizing.
- Do not use this skill when the main task is runtime incident capture from a live JVM rather than performance interpretation.

## Common-Case Workflow

1. Read the target code, hot path, and the nearest benchmark, trace, or diagnostic evidence.
2. Identify whether the issue is latency, throughput, contention, or allocation.
3. Profile or interpret measured evidence before recommending a concurrency or optimization change.
4. Prefer the smallest measured change that addresses the observed bottleneck.

## First Runnable Commands or Code Shape

Start with a profiler-ready JVM launch shape:

```bash
java -XX:StartFlightRecording=duration=60s,filename=profile.jfr,settings=profile -jar app.jar
```

*Applies when:* you need a first real profile for latency, throughput, allocation, or lock analysis.

## Ready-to-Adapt Templates

Blocking-I/O review (use when the service spends more time waiting than computing):

```text
# 1. Confirm: network, disk, or external services dominate the hot path.
# 2. Check: thread count or thread blocking is the real limiter.
# 3. Consider virtual threads only if blocking tasks dominate.
```

Allocation-pressure review (use when evidence points to churn and GC pressure rather than lock contention):

```text
# 1. Identify allocation-heavy paths from a profile.
# 2. Check: object churn, serialization, or parsing dominates.
# 3. Reduce allocation in the hot path before discussing GC tuning.
```

Virtual-thread evaluation (use when the workload is blocking-I/O and the baseline supports virtual threads):

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> service.handle(request));
}
```

Pinned-CPU warning (use when someone proposes virtual threads or concurrency changes as a blanket upgrade):

```text
# 1. CPU-bound hot path → virtual threads are not the first fix.
# 2. JDK 21-23 baseline → review synchronized pinning and thread-local assumptions.
# 3. JDK 24+ baseline → JEP 491 removes synchronized-driven pinning, but native/JNI pinning can still exist.
# 4. Bottleneck is parsing/allocation/serialization → optimize the hot path first.
```

## Validate the Result

Validate the common case with these checks:

- the recommendation is grounded in a measured hot path or captured profile
- the bottleneck is classified as CPU, I/O, contention, or allocation rather than vaguely "performance"
- virtual threads are recommended only for workloads that actually fit them
- the Java baseline is known before version-sensitive virtual-thread caveats are discussed
- allocation or synchronization fixes are tied to observed evidence, not generic advice

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| virtual-thread fit, limits, and review questions | `./references/virtual-threads.md` |
| additional performance-review heuristics | `./references/performance-patterns.md` |

## Invariants

- MUST treat profiling as the source of truth.
- MUST review the concurrency model before recommending primitives.
- MUST distinguish CPU-bound, I/O-bound, and mixed workloads.
- MUST NOT recommend virtual threads as a blanket upgrade.
- MUST keep virtual-thread caveats aligned with the actual Java baseline.
- SHOULD explain the likely cost of synchronization, allocation churn, and context switching.
- SHOULD treat `ScopedValue` as a version-sensitive alternative to broad `ThreadLocal` usage when immutable request context is the real problem; it is preview on Java 21-24 and finalized in Java 25.
- SHOULD prefer simple concurrency models that fit the workload.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| optimizing before profiling | changes target the wrong bottleneck | collect or read evidence first |
| recommending virtual threads for CPU-bound work | thread model does not solve CPU saturation | fix the hot computation path first |
| repeating pre-JDK-24 pinning advice on a newer baseline | the recommendation becomes stale or wrong | state whether the target runtime is Java 21-23 or Java 24+ before warning about `synchronized` pinning |
| talking about throughput and latency as if they were the same goal | the recommended tradeoff becomes incoherent | state the dominant workload goal explicitly |
| blaming GC for allocation-heavy code without checking the hot path | the application-level churn remains unfixed | reduce churn first, then revisit GC only if needed |

## Scope Boundaries

- Activate this skill for:
  - profiling strategy and bottleneck interpretation
  - concurrency model tradeoffs
  - virtual-thread evaluation
- Do not use this skill as the primary source for:
  - public API or type-modeling decisions
  - JUnit structure or test-first workflow
  - runtime incident triage from live JVM evidence
  - standard JDK tool selection or packaging workflows
