---
name: java-performance-concurrency
description: >-
  Use this skill when the user asks to "optimize Java performance", "analyze concurrency design", "use virtual threads", "reduce allocation pressure", "profile Java code", or needs guidance on Java performance and concurrency tradeoffs.
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

These templates stay in `SKILL.md` because performance review is part of the skill's primary purpose and the common path should be usable without opening references.

Blocking-I/O review template:

```yaml
workload: request-per-thread service with blocking network or disk calls
evidence:
  - flame graph or JFR shows waiting time dominating compute time
  - thread dump shows many blocked or parked request threads
baseline: JDK 21+
first_change:
  - isolate the blocking call path
  - confirm the bottleneck is waiting rather than CPU saturation
  - evaluate virtual threads only after the blocking path is confirmed
not_first:
  - generic pool-size increases
  - GC tuning before the waiting path is measured
```

```java
Response load(UserId id) throws IOException {
    HttpResponse<String> response = httpClient.send(requestFor(id), BodyHandlers.ofString());
    return mapper.readValue(response.body(), Response.class);
}
```

Use when: the service spends more time waiting on network, disk, or external systems than computing locally.

Allocation-pressure review template:

```yaml
workload: allocation-heavy request or batch path
evidence:
  - JFR or heap profile shows high allocation rate in one hot path
  - GC pauses or CPU time track object churn rather than lock contention
top_allocators:
  - parser
  - serializer
  - intermediate collections
first_change:
  - reduce transient object creation in the hot path
  - collapse unnecessary intermediate materialization
  - re-measure before discussing collector tuning
not_first:
  - collector swaps without allocation evidence
  - broad object pooling in ordinary code
```

```java
List<Result> parse(List<String> lines) {
    return lines.stream()
        .map(line -> line.trim())
        .filter(line -> !line.isEmpty())
        .map(line -> line.split(","))
        .map(parts -> new Result(parts[0], Integer.parseInt(parts[1])))
        .toList();
}
```

Use when: evidence points to churn and GC pressure caused by parsing, serialization, or intermediate objects rather than lock contention.

Virtual-thread evaluation `(JDK 21+)`:

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> service.handle(request));
}
```

Use when: the workload is blocking-I/O, request concurrency matters, and the baseline supports virtual threads cleanly.

CPU-bound or pinning-risk review template:

```yaml
workload: CPU-bound or mixed CPU plus blocking path
evidence:
  - flame graph dominated by parsing, serialization, crypto, or business computation
  - carrier-thread or pinned-thread warnings appear in tracing or JFR
baseline: JDK 21-23 or JDK 24+
why_virtual_threads_are_not_first:
  - thread model does not remove CPU saturation
  - pre-JDK-24 synchronized pinning guidance still matters on 21-23
  - JDK 24+ removes synchronized-driven pinning, but native or JNI pinning can remain
first_change:
  - optimize the hot computation path or blocking primitive causing pinning
  - then re-evaluate concurrency model changes
not_first:
  - blanket migration to virtual threads
  - lock-free rewrites without evidence
```

```java
synchronized Result parse(byte[] payload) {
    return parser.parse(payload);
}
```

Use when: someone proposes virtual threads or concurrency changes as a blanket upgrade while the hot path is still CPU-bound or pinning-sensitive.

## Validate the Result

Validate the common case with these checks:

- the recommendation is grounded in a measured hot path or captured profile
- the bottleneck is classified as CPU, I/O, contention, or allocation rather than vaguely "performance"
- virtual threads are recommended only for workloads that actually fit them
- the Java baseline is known before version-sensitive virtual-thread caveats are discussed
- allocation or synchronization fixes are tied to observed evidence, not generic advice
- the review template records workload shape, evidence, first change, and what is intentionally not first

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
