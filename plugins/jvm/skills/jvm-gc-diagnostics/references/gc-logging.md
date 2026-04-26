---
title: GC Logging Reference
description: >-
  Reference for GC log configuration, output format interpretation, and version-specific
  logging syntax across JDK 8 (legacy) and JDK 9+ (unified logging).
---

Use this reference when the blocker is configuring GC logging for the next deploy, interpreting existing GC log output, or translating between JDK 8 legacy flags and JDK 9+ unified logging syntax.

## JDK 9+ Unified Logging (`-Xlog`)

### Basic GC Log Configuration

Standard production GC log (recommended starting point):

```bash
-Xlog:gc*:file=gc-%p-%t.log:uptime,level,tags:filecount=5,filesize=10M
```

Output fields explained:

- `gc*` — wildcard matches all GC-related tag sets (`gc`, `gc+cpu`, `gc+heap`, etc.)
- `file=gc-%p-%t.log` — output file with PID (`%p`) and timestamp (`%t`) substitution
- `uptime` — time since JVM start (alternatives: `uptime`, `timemillis`, `uptimemillis`)
- `level` — include log level (info, debug, trace)
- `tags` — include log tags for filtering
- `filecount=5` — rotate up to 5 files
- `filesize=10M` — each file up to 10 MB

### Refined GC Log Configurations

Pause-focused (for latency-sensitive workloads):

```bash
-Xlog:gc+pause=debug:file=gc-pause.log:uptime,level:filecount=3,filesize=20M
```

Heap-focused (for memory-pressure investigations):

```bash
-Xlog:gc+heap=debug,gc+ref=debug:file=gc-heap.log:uptime,level:filecount=3,filesize=20M
```

Full diagnostic (use temporarily, high volume):

```bash
-Xlog:gc*=debug:file=gc-debug.log:uptime,level,tags:filecount=5,filesize=50M
```

### What Unified GC Logs Look Like

Sample output line (single-line, each GC event produces one or more lines):

```text
[0.034s][info][gc] Using G1
[0.034s][info][gc] Version: 25.0+12 (release mode)
[0.952s][info][gc,start ] GC(0) Pause Young (Normal) (G1 Evacuation Pause)
[0.953s][info][gc      ] GC(0) Pausing to collect heap
[0.956s][info][gc,cpu  ] GC(0) User=0.01s Sys=0.00s Real=0.00s
[1.234s][info][gc,start ] GC(1) Pause Young (Normal) (G1 Evacuation Pause)
[1.235s][info][gc      ] GC(1) Young RemSet occupancy: 0 entries
[1.238s][info][gc,cpu  ] GC(1) User=0.02s Sys=0.00s Real=0.03s
[5.678s][info][gc,start ] GC(2) Pause Full (System.gc())
[5.679s][info][gc,heap  ] GC(2) Heap before GC: 256M(512M)
[5.682s][info][gc,heap  ] GC(2) Heap after GC: 128M(512M)
[5.683s][info][gc,cpu  ] GC(2) User=0.05s Sys=0.01s Real=0.04s
```

Key fields to read from each event:

| Field | Location | How to read it |
| --- | --- | --- |
| Timestamp | `[0.952s]` | Uptime in seconds since JVM start |
| Level | `[info]`, `[debug]`, `[trace]` | Verbosity of the message |
| Tag set | `[gc]`, `[gc,cpu]`, `[gc,heap]` | Topic area for filtering |
| GC ID | `GC(0)`, `GC(1)` | Unique identifier per GC cycle |
| Phase | `Pause Young (Normal)` | Type of collection phase |
| User time | `User=0.01s` | CPU time spent in user space |
| System time | `Sys=0.00s` | CPU time spent in kernel space |
| Real time | `Real=0.00s` | Wall-clock pause duration (**this is the pause metric**) |

### Inspecting Active Logging at Runtime

List currently configured log outputs:

```bash
jcmd <pid> VM.log list
```

Add a GC log output dynamically (JDK 9+):

```bash
jcmd <pid> VM.log output=/path/to/gc-dynamic.log what="gc*=info"
```

Sample `VM.log list` output:

```text
#0: stdout  gc*  info
#1: file=gc.log  gc*  info  filecount=5  filesize=10m  uptime
```

## JDK 8 Legacy Logging

### Basic Legacy GC Log Flags

Standard JDK 8 GC logging flags:

```bash
-verbose:gc
-XX:+PrintGCDetails
-XX:+PrintGCTimeStamps
-XX:+PrintGCDateStamps
-Xloggc:gc.log
```

### What Legacy GC Logs Look Like

Sample output:

```text
2026-04-20T10:15:30.123+0000: 1.234: [GC (Allocation Failure) [PSYoungGen: 65536K->8192K(131072K)] 196608K->139264K(524288K), 0.0234321 secs] [Times: user=0.02 sys=0.00, real=0.02 secs]
2026-04-20T10:15:31.456+0000: 2.567: [Full GC (Metadata GC Threshold) [Tenured: 131072K->98304K(262144K)] 196608K->139264K(524288K), [Metaspace: 65536K->61440K(131072K)], 0.1567890 secs] [Times: user=0.14 sys=0.01, real=0.16 secs]
```

Key fields to read from legacy output:

| Field | Location | How to read it |
| --- | --- | --- |
| Datestamp | `2026-04-20T10:15:30.123+0000` | Wall-clock time (with `-XX:+PrintGCDateStamps`) |
| Timestamp | `1.234:` | Seconds since JVM start (with `-XX:+PrintGCTimeStamps`) |
| GC type | `[GC ...]` vs `[Full GC ...]` | Young GC vs Full GC |
| Generation detail | `[PSYoungGen: 65536K->8192K(131072K)]` | Before->After(Total) for that generation |
| Total heap | `196608K->139264K(524288K)` | Entire heap Before->After(Max) |
| Duration | `0.0234321 secs` | Wall-clock pause time |
| Times | `[Times: user=... sys=..., real=...]` | CPU and real-time breakdown |

## Translation Table: Legacy to Unified

| JDK 8 Legacy Flag | JDK 9+ Unified Equivalent |
| --- | --- |
| `-verbose:gc` | `-Xlog:gc` |
| `-XX:+PrintGCDetails` | `-Xlog:gc*` (or `gc*=debug` for more detail) |
| `-XX:+PrintGCTimeStamps` | `uptime` or `timemillis` in output decorator |
| `-XX:+PrintGCDateStamps` | `time` in output decorator |
| `-Xloggc:gc.log` | `file=gc.log` in output decorator |
| `-XX:+PrintGCApplicationConcurrentTime` | `-Xlog:safepoint+application` |
| `-XX:+PrintGCApplicationStoppedTime` | `-Xlog:safepoint` |
| `-XX:+PrintTenuringDistribution` | `-Xlog:gc+age=trace` |
| `-XX:+PrintHeapAtGC` | Not directly equivalent; use `-Xlog:gc+heap=debug` |

## Red Flags in GC Log Output

Watch for these patterns when reading GC logs:

| Pattern | What it suggests | Next step |
| --- | --- | --- |
| Frequent Full GC cycles | Heap too small, memory leak, or premature promotion | Increase heap, check for leaks with `GC.class_histogram` |
| Consistently long young GC pauses (>500ms) | Large live set, wrong collector, or fragmentation | Consider ZGC for low-latency, or tune G1 region size |
| `To-space exhausted` messages | Allocation rate outpaces evacuation capacity | Increase reserve or heap size |
| `Allocation Failed` repeated | Object promotion failure triggering Full GC | Tune tenuring threshold or increase old gen size |
| Very short intervals between GC events (<100ms) | High allocation rate, small heap | Profile allocation with JFR `ObjectAllocationInNewTLAB` |
| Metaspace OOM | Class loading leak or insufficient metaspace budget | Check class loader retention, increase `MetaspaceSize` |
