---
description: >-
  Reference for JFR GC-related events, recording configurations, analysis commands, and output interpretation for garbage-collection diagnostics.
---

# JFR GC Events Reference

Use this reference when the blocker is analyzing GC behavior through JFR recordings, choosing which GC events to capture, or interpreting JFR GC event output from `jfr print`.

The examples below target the JDK 11+ JFR event schema.
Verify event names and fields with `jfr metadata` on the target runtime before relying on them.
JDK 8 availability varies by vendor and update: Oracle JDK 8 requires the commercial Flight Recorder feature and licensing check, while some OpenJDK 8 builds omit JFR entirely.
The `-Xlog` options shown here are JDK 9+ unified logging syntax.
Use JDK 8 legacy GC logging flags instead.

## GC-Focused JFR Recording Templates

### Low-Overhead GC Baseline Recording

Start on a running JVM:

```sh
jcmd <pid> JFR.start name=gc-baseline settings=default disk=true maxage=2h
```

This uses the `default` preset, which Oracle documents as recommended for continuous recordings with a good balance between data and performance, typically less than 1% overhead.
Treat the percentage as a `default.jfc` baseline, not as a guarantee for custom event settings.

### GC-Diagnostic Recording (Higher Overhead)

When you need detailed GC event breakdown:

```sh
jcmd <pid> JFR.start name=gc-detail settings=profile disk=true maxage=1h
```

The `profile` preset adds allocation and CPU events useful for correlating GC pressure with application behavior.

### Startup-Attached GC Recording

Use this command when you need simultaneous GC + JFR evidence from process start:

```sh
java \
  -XX:StartFlightRecording=name=gc-startup,settings=profile,filename=/path/to/private-diagnostics/gc-startup.jfr,dumponexit=true \
  -Xlog:gc=debug:file=/path/to/private-diagnostics/gc-%p-%t.log:uptimemillis,pid:filecount=5,filesize=10M \
  -jar app.jar
```

Use when the GC symptom appears during startup warmup or early request phases.

Expected artifacts:

```text
/path/to/private-diagnostics/gc-startup.jfr
```

## Key JFR GC Events

These are the primary events to query when analyzing GC behavior from JFR data.

### Core GC Events

| Event name | What it reports | When to query |
| --- | --- | --- |
| `jdk.GCConfiguration` | Collector type, pool sizes, thread counts | Confirm active collector and settings |
| `jdk.GCHeapSummary` | Heap usage after each GC phase | Track heap occupancy over time |
| `jdk.GCPhasePause` | Individual pause phase durations (`duration`, `name`, `gcId`) | Identify longest pause phases |
| `jdk.GarbageCollection` | GC cycle summary (`cause`, `duration`, `name`, `sumOfPauses`, `longestPause`) | Correlate pauses with causes |
| `jdk.YoungGarbageCollection` | Young generation GC details | Analyze young gen frequency and efficiency |
| `jdk.OldGarbageCollection` | Old generation / full GC details | Investigate Full GC triggers |
| `jdk.GCHeapMemoryUsage` | Heap usage samples (`used`, `committed`, `max`) | Track total heap trends |
| `jdk.TenuringDistribution` | Object age distribution at young GC | Diagnose premature promotion patterns |

### Allocation-Related Events

| Event name | What it reports | When to query |
| --- | --- | --- |
| `jdk.ObjectAllocationInNewTLAB` | Allocations in new TLAB (hot paths) | Find allocation-heavy code paths |
| `jdk.ObjectAllocationOutsideTLAB` | Allocations outside TLAB (large objects) | Find large-object allocation sites |
| `jdk.ObjectAllocationSample` | Statistical allocation sampling | Lightweight allocation profiling |

### Scheduling and Latency Events

| Event name | What it reports | When to query |
| --- | --- | --- |
| `jdk.ThreadContextSwitchRate` | Context switch frequency | Detect scheduling pressure from GC threads |
| `jdk.CPULoad` | Per-process CPU utilization | Correlate GC CPU cost with total CPU load |
| `jdk.ExecutionSample` | Thread stack samples (profiling) | Map GC stalls to application code |

## Analysis Commands

### Quick Summary Overview

```sh
jfr summary /path/to/private-diagnostics/recording.jfr
```

This shows event counts and other high-level recording contents.
Start here before using `jfr print` for event extraction.

### Query Specific GC Events

Show all GarbageCollection events with key fields:

```sh
jfr print --events "jdk.GarbageCollection" /path/to/private-diagnostics/recording.jfr
```

Show GC pause phases with durations:

```sh
jfr print --events "jdk.GCPhasePause" /path/to/private-diagnostics/recording.jfr
```

Show heap summaries to track occupancy changes:

```sh
jfr print --events "jdk.GCHeapSummary" /path/to/private-diagnostics/recording.jfr
```

Show GC configuration to confirm collector identity:

```sh
jfr print --events "jdk.GCConfiguration" /path/to/private-diagnostics/recording.jfr
```

### Filter by Time Window

Portable note: do not use relative `--beginTime 60s --endTime 120s` examples here.
For a known incident window, first extract the target event stream and then apply a separately verified time-slicing workflow for the target JDK/tooling stack.

```sh
jfr print --events "jdk.GarbageCollection" /path/to/private-diagnostics/recording.jfr
```

### JSON Output for Programmatic Analysis

```sh
jfr print --json --events "jdk.GCPhasePause" /path/to/private-diagnostics/recording.jfr
```

### Top Stacks for GC-Related Events

Which call sites trigger the most allocation:

```sh
jfr print --events "jdk.ObjectAllocationInNewTLAB" \
  --stack-depth 5 /path/to/private-diagnostics/recording.jfr
```

Thread context switches correlated with GC:

```sh
jfr print --events "jdk.ThreadContextSwitchRate" /path/to/private-diagnostics/recording.jfr
```

## Sample Output Interpretation

### `jdk.GCConfiguration` Output

```text
jdk.GCConfiguration {
  startTime = 0.042 s
  youngCollector = "G1 New"
  oldCollector = "G1 Old"
  parallelGCThreads = 6
  concurrentGCThreads = 2
  usesDynamicGCThreads = false
  isExplicitGCConcurrent = false
  isExplicitGCDisabled = false
}
```

Read: This confirms G1 is active with 6 parallel and 2 concurrent GC threads.
If `youngCollector` says "ParallelScavenge", the runtime is using Parallel GC, not G1.

### `jdk.GarbageCollection` Output

```text
jdk.GarbageCollection {
  startTime = 123.456 s
  duration = 25 ms
  gcId = 42
  name = "G1 Young Generation"
  cause = "G1 Evacuation Pause"
  sumOfPauses = 23 ms
  longestPause = 12 ms
}
```

Read: A young GC cycle (G1 evacuation) took 23ms total with the longest single phase at 12ms.
The `cause` field tells you why this GC happened.

### `jdk.GCHeapSummary` Output (after GC)

```text
jdk.GCHeapSummary {
  startTime = 123.480 s
  gcId = 42
  when = "After GC"
  heapSpace = {
    committedSize = 256 MB
    reservedSize = 512 MB
  }
  heapUsed = 48 MB
}
```

Read: After GC, 48MB of the 512MB reserved heap is used (9% utilization).
Use `jdk.GCHeapMemoryUsage` when you need `used`, `committed`, and `max` heap measurements at sample points.

### `jdk.ObjectAllocationInNewTLAB` Output (top allocations)

```text
jdk.ObjectAllocationInNewTLAB {
  startTime = 200.123 s
  eventThread = "http-nio-8080-exec-3"
  objectClass = "byte[]"
  allocationSize = 1024 B
  tlabSize = 16384 B
  stackTrace = [
    "java.io.ByteArrayOutputStream.write",
    "com.example.handler.processRequest",
    ...
  ]
}
```

Read: The `http-nio-8080-exec-3` thread is allocating byte arrays in `processRequest`.
Repeated events with the same stack indicate an allocation hot spot driving GC pressure.

## Decision Guide: GC Evidence Type

| Question | Best evidence source | Command |
| --- | --- | --- |
| What collector is running? | `jdk.GCConfiguration` + `jcmd VM.flags` | `jfr print --events jdk.GCConfiguration` |
| How long do pauses last? | `jdk.GCPhasePause` + GC logs | `jfr print --events jdk.GCPhasePause` |
| Is the heap filling up? | `jdk.GCHeapSummary` trend | `jfr print --events jdk.GCHeapSummary` |
| What allocates the most? | `jdk.ObjectAllocationInNewTLAB` | `jfr print --events jdk.ObjectAllocationInNewTLAB --stack-depth 5` |
| Is GC causing CPU saturation? | `jdk.CPULoad` + `jdk.GarbageCollection` timing | Cross-reference timestamps |
| Are objects dying young or being promoted? | `jdk.TenuringDistribution` | `jfr print --events jdk.TenuringDistribution` |
