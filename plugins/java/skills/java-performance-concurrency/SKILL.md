---
name: java-performance-concurrency
description: >-
  Review Java performance and concurrency decisions with evidence-driven profiling,
  classify bottlenecks as CPU, I/O, contention, or allocation,
  evaluate virtual-thread fit, and recommend the smallest measured change.
  Use when the user asks to optimize Java performance, analyze concurrency design,
  use virtual threads, reduce allocation pressure, profile Java code,
  or needs guidance on Java performance and concurrency tradeoffs.
---

# Java Performance Concurrency

Review Java performance and concurrency decisions with emphasis on evidence, workload shape, and modern JVM capabilities. The common case is not heroic optimization; it is identifying whether the bottleneck is CPU, blocking I/O, contention, or allocation churn, then making the smallest measured change that fits that shape.

## Operating rules

- MUST treat profiling as the source of truth.
- MUST review the concurrency model before recommending primitives.
- MUST distinguish CPU-bound, I/O-bound, and mixed workloads.
- MUST NOT recommend virtual threads as a blanket upgrade.
- MUST keep virtual-thread caveats aligned with the actual Java baseline.
- SHOULD explain the likely cost of synchronization, allocation churn, and context switching.
- SHOULD prefer simple concurrency models that fit the workload.
- SHOULD treat `ScopedValue` as a version-sensitive alternative to broad `ThreadLocal` usage when immutable request context is the real problem; it is preview on Java 21-24 and finalized in Java 25.

## Procedure

1. Read the target code, hot path, and the nearest benchmark, trace, or diagnostic evidence.
2. Identify whether the issue is latency, throughput, contention, or allocation.
3. Profile or interpret measured evidence before recommending a concurrency or optimization change.
4. Classify the workload shape before recommending any specific primitive.
5. Prefer the smallest measured change that addresses the observed bottleneck.

## First runnable commands

Start with a profiler-ready JVM launch shape:

```bash
java -XX:StartFlightRecording=duration=60s,filename=profile.jfr,settings=profile -jar app.jar
```

Use when: you need a first real profile for latency, throughput, allocation, or lock analysis.

## Ready-to-adapt templates

### JMH microbenchmark skeleton

```java
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Measurement;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class ParserBenchmark {

    private final byte[] payload = "key=value,name=test".getBytes();

    @Benchmark
    public Result parse() {
        return Parser.parse(payload);
    }
}
```

Maven dependency:

```xml
<dependency>
  <groupId>org.openjdk.jmh</groupId>
  <artifactId>jmh-core</artifactId>
  <version>${verifiedVersion}</version>
</dependency>
<dependency>
  <groupId>org.openjdk.jmh</groupId>
  <artifactId>jmh-generator-annprocess</artifactId>
  <version>${verifiedVersion}</version>
  <scope>provided</scope>
</dependency>
```

Gradle Groovy DSL:

```groovy
def jmhVersion = "${verifiedVersion}"
dependencies {
    implementation "org.openjdk.jmh:jmh-core:$jmhVersion"
    annotationProcessor "org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion"
}
```

Gradle Kotlin DSL:

```kotlin
val jmhVersion = "${verifiedVersion}"
dependencies {
    implementation("org.openjdk.jmh:jmh-core:$jmhVersion")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")
}
```

### GC selection decision tree

| Collector | Flag | Best for | Avoid when |
| --- | --- | --- | --- |
| G1 (default) | `-XX:+UseG1GC` | General-purpose, balanced latency/throughput | Sub-millisecond pause requirements |
| ZGC | `-XX:+UseZGC` | Low-latency, large heaps (JDK 15+ production-ready) | Very small heaps, JDK 11 |
| Shenandoah | `-XX:+UseShenandoahGC` | Low-latency alternative to ZGC | Not available in all JDK distributions |
| Parallel | `-XX:+UseParallelGC` | Batch/throughput-focused, max throughput | Latency-sensitive services |

Rule: do not change the collector without allocation and pause evidence from JFR or GC logs.

### Blocking-I/O review

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
import java.io.IOException;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

Response load(UserId id) throws IOException {
    HttpResponse<String> response = httpClient.send(requestFor(id), BodyHandlers.ofString());
    return mapper.readValue(response.body(), Response.class);
}
```

### Allocation-pressure review

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
import java.util.List;

List<Result> parse(List<String> lines) {
    return lines.stream()
        .map(line -> line.trim())
        .filter(line -> !line.isEmpty())
        .map(line -> line.split(","))
        .map(parts -> new Result(parts[0], Integer.parseInt(parts[1])))
        .toList();
}
```

### Virtual-thread evaluation `(JDK 21+)`

```java
import java.util.concurrent.Executors;

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> service.handle(request));
}
```

### Synchronization primitive selection

| Primitive | Use when | Avoid when |
| --- | --- | --- |
| `synchronized` | Simple mutual exclusion, low contention | Virtual threads on JDK 21-23 (pinning risk) |
| `ReentrantLock` | Need `tryLock()`, timed lock, or interruptible lock | Simple cases where `synchronized` suffices |
| `ReadWriteLock` | Many readers, few writers | Write-heavy or low-contention workloads |
| `AtomicInteger` / `AtomicReference` | Single-variable atomic updates | Multi-variable compound actions |
| `Semaphore` | Limiting concurrent access to a resource | Simple mutual exclusion (use `synchronized` or `ReentrantLock`) |
| `CountDownLatch` | One-time event waiting for N tasks to complete | Repeated reset needed (use `CyclicBarrier`) |

### CPU-bound or pinning-risk review

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

### Heap dump capture

On out-of-memory:

```bash
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heap.hprof -jar app.jar
```

On demand:

```bash
jcmd <pid> GC.heap_dump /tmp/heap.hprof
```

Analyze with Eclipse MAT or `jhsdb`:

```bash
jhsdb hat dump --heap-dump-file /tmp/heap.hprof
```

## Edge cases

- If the main task is runtime incident capture from a live JVM rather than performance interpretation, state that live incident triage is outside this skill's scope.
- If the question is about public API or type-modeling decisions, that is outside this skill's scope.
- If the question is about JUnit structure or test-first workflow, that is outside this skill's scope.
- If no profiling evidence exists, collect evidence before recommending any change.
- If someone proposes virtual threads for CPU-bound work, reject the recommendation and point to the hot computation path first.
- If discussing `synchronized` pinning, state whether the target runtime is Java 21-23 or Java 24+ before giving version-specific advice.
- Standard JDK tool selection or packaging workflows are outside this skill's scope.

## Output contract

Return:

1. The classified bottleneck type (CPU, I/O, contention, or allocation).
2. The measured evidence that supports the classification.
3. The recommended change tied to that evidence.
4. What was intentionally deferred and why.

## Support-file pointers

| If the blocker is... | Open... |
| --- | --- |
| virtual-thread fit, limits, ScopedValue usage, pinning diagnosis | [`virtual-threads.md`](./references/virtual-threads.md) |
| additional profiling commands, JFR attach patterns, evidence interpretation heuristics | [`performance-patterns.md`](./references/performance-patterns.md) |

## Gotchas

- Do not optimize before profiling.
- Do not recommend virtual threads for CPU-bound work.
- Do not repeat pre-JDK-24 pinning advice on a newer baseline without stating the version range.
- Do not talk about throughput and latency as if they were the same goal.
- Do not blame GC for allocation-heavy code without checking the hot path first.
