---
title: Thread Dumps and JFR Reference
description: >-
  Reference for repeated thread-dump capture and JFR recording during JVM runtime diagnostics.
---

Use official JDK tool documentation from the Oracle Java docs hub: <https://docs.oracle.com/en/java/>

Use this reference when the issue needs either repeated snapshot comparison or time-based runtime evidence and the remaining blocker is choosing or running the capture.

## Thread Dumps

Single thread dump:

```bash
jcmd <pid> Thread.print -l > thread-1.txt
```

Three-pass comparison for blocking or contention:

```bash
for i in 1 2 3; do
    jcmd <pid> Thread.print -l > "thread-$i.txt"
    sleep 5
done
```

Legacy standalone form:

```bash
jstack -l <pid> > thread-1.txt
```

Key things to look for:

- `state=BLOCKED` threads waiting on a lock
- `state=WAITING` or `TIMED_WAITING` threads that do not recover
- `waiting on` vs `waiting for` to identify the held monitor
- repeated appearances of the same blocked stack in multiple captures
- `Deadlock` section in legacy `jstack -l` output

## JFR Recording

Start a low-overhead recording:

```bash
jcmd <pid> JFR.start name=baseline settings=default disk=true maxage=6h
jcmd <pid> JFR.check
```

Start JFR at JVM launch:

```bash
java -XX:StartFlightRecording=name=startup,settings=profile,filename=/tmp/startup.jfr,dumponexit=true -jar app.jar
```

- use startup-attached JFR when the failure window may happen before attach is practical
- keep `dumponexit=true` when the process may terminate before an explicit `JFR.dump`

Dump after the workload window:

```bash
jcmd <pid> JFR.dump name=baseline filename=/tmp/baseline-%p-%t.jfr
```

Stop a recording cleanly:

```bash
jcmd <pid> JFR.stop name=baseline
```

Profile-oriented recording:

```bash
jcmd <pid> JFR.start name=profile settings=profile disk=true maxage=2h
```

Quick analysis commands:

```bash
jfr print --summary /tmp/baseline.jfr
jfr print --json --events "jdk.JavaMonitorEnter" /tmp/baseline.jfr
```

Key JFR events:

- `jdk.ObjectAllocationInNewTLAB` for allocation-heavy hot paths
- `jdk.JavaMonitorEnter` for lock contention
- `jdk.ContextSwitchRate` for scheduling overhead
- `jdk.GCHeapSummary` for heap occupation over time
- `jdk.CPULoad` for per-process CPU saturation

## Choosing Between Thread Dumps and JFR

| Symptom | First choice | Why |
| --- | --- | --- |
| blocking, deadlock, starvation | repeated thread dumps | snapshot comparison shows who holds what |
| CPU or allocation hot path | short JFR recording | time-based evidence identifies the heaviest code paths |
| lock contention over time | JFR with `jdk.JavaMonitorEnter` | event frequency and duration per lock |
| unexplained latency spikes | JFR with latency events | time-based evidence shows where threads stall |
| startup-only failures or very early regressions | startup-attached JFR | evidence begins before a later `jcmd` attach would be possible |
