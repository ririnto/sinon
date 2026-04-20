---
title: JFR GC Events Reference
description: >-
  Reference for JFR GC-related events, recording configurations, analysis commands,
  and output interpretation for garbage-collection diagnostics.
---

Use this reference when the blocker is analyzing GC behavior through JFR recordings, choosing which GC events to capture, or interpreting JFR GC event output from `jfr print`.

## GC-Focused JFR Recording Templates

### Low-Overhead GC Baseline Recording

Start on a running JVM:

```bash
jcmd <pid> JFR.start name=gc-baseline settings=default disk=true maxage=2h
```

This uses the `default` preset which includes core GC events at low overhead (~1%).

### GC-Diagnostic Recording (Higher Overhead)

When you need detailed GC event breakdown:

```bash
jcmd <pid> JFR.start name=gc-detail settings=profile disk=true maxage=1h
```

The `profile` preset adds allocation and CPU events useful for correlating GC pressure with application behavior.

### Startup-Attached GC Recording

This extends the startup-attached JFR template from the parent SKILL.md **Ready-to-Adapt Templates** section by adding unified GC logging for simultaneous GC + JFR evidence capture.

```bash
java \
  -XX:StartFlightRecording=name=gc-startup,settings=profile,\
filename=/path/to/private-diagnostics/gc-startup.jfr,dumponexit=true \
  -Xlog:gc=debug:file=gc-%p-%t.log:uptimemillis,pid:filecount=5,filesize=10M \
  -jar app.jar
```

Use when the GC symptom appears during startup warmup or early request phases.

## Key JFR GC Events

These are the primary events to query when analyzing GC behavior from JFR data.

### Core GC Events

| Event name | What it reports | When to query |
| --- | --- | --- |
| `jdk.GCConfiguration` | Collector type, pool sizes, thread counts | Confirm active collector and settings |
| `jdk.GCHeapSummary` | Heap usage after each GC phase | Track heap occupancy over time |
| `jdk.GCPause` or `jdk.GCPhasePause` | Individual pause phase durations | Identify longest pause phases |
| `jdk.GarbageCollection` | GC cycle summary (cause, duration, threads involved) | Correlate pauses with causes |
| `jdk.YoungGarbageCollection` | Young generation GC details | Analyze young gen frequency and efficiency |
| `jdk.OldGarbageCollection` | Old generation / full GC details | Investigate Full GC triggers |
| `jdk.GCHeapMemoryUsage` | Memory pool usage at sample points | Track Eden/Survivor/Old/Metaspace trends |
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

```bash
jfr print --summary /path/to/recording.jfr
```

This shows event counts, durations, and top stacks. Start here to understand what the recording contains.

### Query Specific GC Events

Show all GarbageCollection events with key fields:

```bash
jfr print --events "jdk.GarbageCollection" /path/to/recording.jfr
```

Show GC pause phases with durations:

```bash
jfr print --events "jdk.GCPhasePause" /path/to/recording.jfr
```

Show heap summaries to track occupancy changes:

```bash
jfr print --events "jdk.GCHeapSummary" /path/to/recording.jfr
```

Show GC configuration to confirm collector identity:

```bash
jfr print --events "jdk.GCConfiguration" /path/to/recording.jfr
```

### Filter by Time Window

Events during a known problem window (e.g., 60-120 seconds after start):

```bash
jfr print --beginTime 60s --endTime 120s --events "jdk.GarbageCollection" /path/to/recording.jfr
```

### JSON Output for Programmatic Analysis

```bash
jfr print --json --events "jdk.GCPhasePause" /path/to/recording.jfr
```

### Top Stacks for GC-Related Events

Which call sites trigger the most allocation:

```bash
jfr print --events "jdk.ObjectAllocationInNewTLAB" \
  --stack-depth 5 /path/to/recording.jfr
```

Thread context switches correlated with GC:

```bash
jfr print --events "jdk.ThreadContextSwitchRate" /path/to/recording.jfr
```

## Sample Output Interpretation

### `jdk.GCConfiguration` Output

```text
jdk.GCConfiguration {
  startTime = 0.042 s
  youngCollector = "G1 New"
  oldCollector = "G1 Old"
  concurrentProcessors = 0
  useThreadLocalAllocation = true
  numberOfGCThreads = 6
}
```

Read: This confirms G1 is active with 6 GC thread. If `youngCollector` says "ParallelScavenge", the runtime is using Parallel GC, not G1.

### `jdk.GarbageCollection` Output

```text
jdk.GarbageCollection {
  startTime = 123.456 s
  cause = "G1 Evacuation Pause"
  sumOfPauses = 23 ms
  longestPause = 12 ms
  name = "GC(42)"
}
```

Read: A young GC cycle (G1 evacuation) took 23ms total with the longest single phase at 12ms. The `cause` field tells you why this GC happened.

### `jdk.GCHeapSummary` Output (after GC)

```text
jdk.GCHeapSummary {
  startTime = 123.480 s
  when = "After GC"
  heapUsed = 48 MB
  heapCommitted = 256 MB
  heapMax = 512 MB
  edenSpaceUsed = 16 MB
  survivorSpaceUsed = 4 MB
  oldObjectSpaceUsed = 28 MB
}
```

Read: After GC, 48MB of 512MB max heap is used (9% utilization). If `heapUsed` approaches `heapMax` frequently, the heap is undersized.

### `jdk.ObjectAllocationInNewTLAB` Output (top allocations)

```text
jdk.ObjectAllocationInNewTLAB {
  startTime = 200.123 s
  eventThread = "http-nio-8080-exec-3"
  className = "byte[]"
  allocationSize = 1024 B
  tlabSize = 16384 B
  stackTrace = [
    "java.io.ByteArrayOutputStream.write",
    "com.example.handler.processRequest",
    ...
  ]
}
```

Read: The `http-nio-8080-exec-3` thread is allocating byte arrays in `processRequest`. Repeated events with the same stack indicate an allocation hot spot driving GC pressure.

## Decision Guide: GC Evidence Type

| Question | Best evidence source | Command |
| --- | --- | --- |
| What collector is running? | `jdk.GCConfiguration` + `jcmd VM.flags` | `jfr print --events jdk.GCConfiguration` |
| How long do pauses last? | `jdk.GCPhasePause` + GC logs | `jfr print --events jdk.GCPhasePause` |
| Is the heap filling up? | `jdk.GCHeapSummary` trend | `jfr print --events jdk.GCHeapSummary` |
| What allocates the most? | `jdk.ObjectAllocationInNewTLAB` | `jfr print --events jdk.ObjectAllocationInNewTLAB --stack-depth 5` |
| Is GC causing CPU saturation? | `jdk.CPULoad` + `jdk.GarbageCollection` timing | Cross-reference timestamps |
| Are objects dying young or being promoted? | `jdk.TenuringDistribution` | `jfr print --events jdk.TenuringDistribution` |
