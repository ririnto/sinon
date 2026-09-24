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

    /**
     * Measures parser throughput over the fixed benchmark payload.
     */
    @Benchmark
    public Result parse() {
        return Parser.parse(payload);
    }
}
```

For a new JMH version, check `org.openjdk.jmh:jmh-core` and `org.openjdk.jmh:jmh-generator-annprocess` on Maven Central for the latest stable compatible release.
Keep the project's BOM, version catalog, or existing pin unless the task authorizes changing it.
Use the same verified JMH version for both artifacts.

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

Default to G1 unless measured evidence points elsewhere.
Justify any collector change with JFR allocation profiles and GC pause logs from the real workload, and confirm pause behavior on the target heap size before rollout.
This skill stops at the selection decision.

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
import java.util.function.Predicate;

List<Result> parse(List<String> lines) {
    return lines.stream()
        .map(String::trim)
        .filter(Predicate.not(String::isEmpty))
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

```sh
java -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heap.hprof -jar app.jar
```

On demand:

```sh
jcmd <pid> GC.heap_dump /tmp/heap.hprof
```

Analyze with Eclipse MAT, or capture from a live JVM with `jhsdb jmap`:

```sh
jhsdb jmap --binaryheap --dumpfile /tmp/heap.hprof --pid <pid>
```

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
