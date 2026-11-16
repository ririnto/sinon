---
metadata:
  reference:
    JEP 505:
      version: JDK 25
      url: https://openjdk.org/jeps/505
    JEP 525:
      version: JDK 26
      url: https://openjdk.org/jeps/525
    JEP 533:
      version: JDK 27
      url: https://openjdk.org/jeps/533
    StructuredTaskScope:
      version: Java SE 27
      url: https://docs.oracle.com/en/java/javase/27/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html
    JDK release status:
      version: JDK 27
      url: https://openjdk.org/projects/jdk/27/
    jcmd:
      - version: JDK 25
        url: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html
      - version: JDK 27
        url: https://docs.oracle.com/en/java/javase/27/docs/specs/man/jcmd.html
name: java-performance-concurrency
description: >-
  Diagnose Java performance bottlenecks or evaluate concurrency and virtual-thread changes using measured evidence.
---

# Java Performance Concurrency

Treat Java 17 as the ordinary floor, Java 21+ as the virtual-thread line, and Java 25+ as the finalized `ScopedValue` line.
Confirm the target runtime before recommending version-sensitive concurrency changes.

Review Java performance and concurrency decisions with emphasis on evidence, workload shape, and modern JVM capabilities.
The common case is not heroic optimization.
It is identifying whether the bottleneck is CPU, blocking I/O, contention, or allocation churn, then making the smallest measured change that fits that shape.

## Operating rules

- MUST treat profiling as the source of truth.
- MUST review the concurrency model before recommending primitives.
- MUST distinguish CPU-bound, I/O-bound, and mixed workloads.
- MUST NOT recommend virtual threads as a blanket upgrade.
- MUST keep virtual-thread caveats aligned with the actual Java baseline.
- SHOULD explain the likely cost of synchronization, allocation churn, and context switching.
- SHOULD prefer simple concurrency models that fit the workload.
- SHOULD treat `ScopedValue` as a version-sensitive alternative to broad `ThreadLocal` usage when immutable request context is the real problem.
  - It is preview on Java 21-24 and finalized in Java 25.

## Task Context

Read the relevant hot path and available benchmark, trace, or profile before choosing an optimization.
Use [`virtual-threads.md`](./references/virtual-threads.md) for virtual-thread limits, `ScopedValue`, or pinning diagnosis.
Use [`performance-patterns.md`](./references/performance-patterns.md) for profiling commands and evidence interpretation.
Collect only the missing evidence needed for the decision.
Confirm the target process and capture impact before live diagnostics.
Diagnostic examples do not authorize production attachment, sensitive dump capture, or overwriting existing artifacts.

## First runnable commands

Start with a profiler-ready JVM launch shape:

```sh
java -XX:StartFlightRecording=duration=60s,filename=profile.jfr,settings=profile -jar app.jar
```

Use when: you need a first real profile for latency, throughput, allocation, or lock analysis.

Use [performance templates](./references/performance-templates.md) when benchmarking, investigating GC or allocation, selecting synchronization, evaluating virtual threads, or capturing a heap dump.

## Edge cases

- Live JVM incident triage is outside this skill's scope.
  Use existing evidence or propose a bounded capture when the target or authority is unclear.
- Public API or type-modeling decisions are outside this skill's scope.
  Use `java:java-language-design`.
- JUnit structure or test-first workflow is outside this skill's scope.
  Use `java:java-test`.
- If no profiling evidence exists, collect evidence before recommending any change.
- If someone proposes virtual threads for CPU-bound work, reject the recommendation and point to the hot computation path first.
- If discussing `synchronized` pinning, state whether the target runtime is Java 21-23 or Java 24+ before giving version-specific advice.
- Standard JDK tool selection or packaging workflows are outside this skill's scope.

## Result

Tie the bottleneck classification and recommendation to measured evidence.
For authorized optimization work, compare the affected workload before and after the change.
State any missing evidence or unverified operational impact.
