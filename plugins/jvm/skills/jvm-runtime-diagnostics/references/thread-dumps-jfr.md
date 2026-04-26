---
title: Thread Dumps and JFR Reference
description: >-
  Reference for repeated thread-dump comparison, JFR analysis, decision guidance between
  snapshot and time-based evidence, and red-flag thread-dump patterns.
---

Use this reference when the issue needs either repeated snapshot comparison or time-based runtime evidence and the remaining blocker is interpreting the capture, choosing between dump and JFR strategies, or recognizing red-flag patterns.

## Choosing Between Thread Dumps and JFR

| Symptom | First choice | Why |
| --- | --- | --- |
| blocking, deadlock, starvation | repeated thread dumps | snapshot comparison shows who holds what |
| CPU or allocation hot path | short JFR recording | time-based evidence identifies the heaviest code paths |
| lock contention over time | JFR with `jdk.JavaMonitorEnter` | event frequency and duration per lock |
| unexplained latency spikes | JFR with latency events | time-based evidence shows where threads stall |
| startup-only failures or very early regressions | startup-attached JFR | evidence begins before a later `jcmd` attach would be possible |

## Three-Pass Comparison Technique

When blocking or contention is suspected, capture three snapshots and compare:

```bash
for i in 1 2 3; do
    jcmd <pid> Thread.print -l > "thread-$i.txt"
    sleep 5
done
diff thread-1.txt thread-3.txt
```

What to look for in the diff:

- Threads that appear in dump 1 but are gone in dump 3 (completed normally)
- Threads stuck in the exact same stack frame across all three dumps (permanently blocked)
- Threads that moved from RUNNABLE to BLOCKED between dumps (degradation in progress)
- New threads appearing in pool patterns (thread creation under load)

## JFR Analysis Commands

```bash
jfr summary /path/to/private-diagnostics/baseline.jfr
jfr print --json --events "jdk.JavaMonitorEnter" /path/to/private-diagnostics/baseline.jfr
```

Key JFR events for runtime diagnostics:

- `jdk.ObjectAllocationInNewTLAB` for allocation-heavy hot paths
- `jdk.JavaMonitorEnter` for lock contention
- `jdk.ThreadContextSwitchRate` for scheduling overhead
- `jdk.GCHeapSummary` for heap occupation over time
- `jdk.CPULoad` for per-process CPU saturation

## Thread Dump Red-Flag Patterns

**All threads stuck in BLOCKED on the same monitor:**

```text
"pool-1-thread-1" BLOCKED on 0x00007f... (a java.lang.Object)
    - waiting to lock <0x00007f...> which is held by "pool-1-thread-2"
"pool-1-thread-3" BLOCKED on 0x00007f... (a java.lang.Object)
    - waiting to lock <0x00007f...> which is held by "pool-1-thread-2"
```

Diagnosis: Classic lock contention. Thread-2 holds a lock that threads 1 and 3 are waiting for.

**Many threads RUNNABLE deep in I/O:**

```text
"http-nio-exec-1" RUNNABLE
    at java.net.SocketInputStream.socketRead0(Native Method)
    at java.net.SocketInputStream.socketRead(SocketInputStream.java:116)
```

Diagnosis: Threads blocked on network I/O (not actually consuming CPU). The RUNNABLE state in native I/O is normal but may indicate slow downstream dependencies.

**Repeated identical stacks across many threads:**

```text
"http-nio-exec-1" ... at com.example.slow.Service.process(Service.java:42)
"http-nio-exec-2" ... at com.example.slow.Service.process(Service.java:42)
"http-nio-exec-3" ... at com.example.slow.Service.process(Service.java:42)
```

Diagnosis: All threads converging on one slow method = bottleneck. Profile this method.

**Deadlock section present (jstack -l format):**

```text
Found one Java-level deadlock:
===================
"Thread-A":
  waiting to lock Monitor@0x00007f..001 which is held by "Thread-B"
"Thread-B":
  waiting to lock Monitor@0x00007f..002 which is held by "Thread-A"
```

Diagnosis: Circular wait — classic deadlock. Stack trace shows exactly which objects/locks are involved.

## Startup-Attached JFR Operational Notes

Use this shape when evidence must start before later attach would be possible:

```bash
java -XX:StartFlightRecording=name=startup,settings=profile,filename=/path/to/private-diagnostics/startup.jfr,dumponexit=true -jar app.jar
```

Expected capture after exit or dump:

```text
/path/to/private-diagnostics/startup.jfr
```

Key operational notes:

- use startup-attached JFR when the failure window may happen before attach is practical
- keep `dumponexit=true` when the process may terminate before an explicit `JFR.dump`
- prefer a private diagnostics directory with restrictive permissions instead of a shared location such as `/tmp`
- on JDK 8, verify the Oracle JDK 8 Flight Recorder availability, licensing posture, and any required commercial-feature unlock before treating startup-attached JFR as a normal path
