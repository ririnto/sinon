---
name: jvm-runtime-diagnostics
description: >-
  Triage JVM runtime incidents with stack traces, thread dumps, jcmd, JFR, and memory-pressure evidence. Use this skill when the user asks to "debug a JVM issue", "read a Java stack trace", "analyze thread dumps", "use jcmd or JFR", "diagnose memory pressure", or needs guidance on JDK runtime incident triage.
metadata:
  title: JVM Runtime Diagnostics
  official_project_url: "https://docs.oracle.com/en/java/"
  reference_doc_urls:
    - "https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/"
    - "https://docs.oracle.com/en/java/javase/11/troubleshoot/diagnostic-tools.html"
    - "https://docs.oracle.com/en/java/javase/17/troubleshoot/diagnostic-tools.html"
    - "https://docs.oracle.com/en/java/javase/21/troubleshoot/diagnostic-tools.html"
    - "https://docs.oracle.com/en/java/javase/25/troubleshoot/diagnostic-tools.html"
  version: "LTS"
---

## Overview

Use this skill to triage JVM runtime problems with standard JDK diagnostic tools and an evidence-first workflow. The common case is not "guess the root cause" but "collect the smallest next capture that reduces uncertainty". Prefer `jcmd` first for live JVMs, keep `jstack` and `jmap` as legacy or narrower-purpose tools, and reserve `jhsdb` for Serviceability Agent cases such as core dumps or deeper postmortem inspection.

Treat JDK 8, 11, 17, 21, and 25 as the supported LTS reference line for this skill, and confirm runtime-specific command availability on the target JVM before assuming a newer flag or event exists.

Treat JFR as the standard low-overhead path on JDK 11 and later. On JDK 8, do not assume JFR is ordinarily available: verify the exact Oracle JDK 8 commercial-feature and licensing posture before recommending `JFR.start` or `-XX:StartFlightRecording`, and prefer thread dumps plus other low-risk captures when that requirement is not clearly satisfied.

## Common-Case Workflow

1. Read the evidence already on hand first: stack trace, logs, thread dump, JFR, or command output.
2. Identify the dominant symptom: blocking, contention, startup failure, memory pressure, crash, or slow path.
3. Start with `jcmd` to confirm the target JVM, the available commands, and the least invasive next capture.
4. Use `jstack` or `jmap` only when you are on an older/legacy workflow or need a specific legacy shape, and use `jhsdb` when you need SA-style inspection, core-dump analysis, or attach alternatives beyond routine `jcmd` workflows.

## Minimal Setup

Run diagnostics on the same machine as the target JVM and, for attach-based tools, with the same effective user/group as the target process.

Tool-selection baseline:

- `jcmd` for normal live-process diagnostics
- `jstack` and `jmap` only when their narrower legacy output shape is specifically useful
- `jhsdb` for core dumps, SA debugger flows, or when live attach is not the normal `jcmd` path

Identify the JVM first:

```bash
jcmd -l
```

> [!NOTE]
> `jcmd -l` lists processes visible to the current user context. In container environments, process visibility may be restricted by namespace boundaries; ensure you are querying from the correct user or namespace context.

## First Runnable Commands or Code Shape

Start with the lowest-risk command sequence:

```bash
jcmd -l
jcmd <pid> help
jcmd <pid> help Thread.print
jcmd <pid> VM.version
jcmd <pid> VM.command_line
jcmd <pid> VM.flags
jcmd <pid> Thread.print -l
```

Use when: the symptom is real but you do not yet know which deeper tool is justified.

## Ready-to-Adapt Templates

Lightweight first triage:

```bash
jcmd -l
jcmd <pid> VM.version
jcmd <pid> VM.flags
jcmd <pid> Thread.print -l
```

Use when: you have only a vague "the JVM is slow" report or one incomplete stack trace.

Single thread dump with lock detail:

```bash
jcmd <pid> Thread.print -l > thread-dump.txt
```

Use when: the issue looks like blocking, deadlock, starvation, or lock contention and you need the first snapshot.

> [!WARNING]
> Thread dumps can contain thread names, stack traces, class names, and other runtime details that may expose request paths or internal system structure. Write thread dumps to a restricted diagnostics path rather than a shared working directory, and clean them up after analysis like other sensitive captures.

Low-overhead JFR start for a running JVM:

```bash
jcmd <pid> JFR.start name=baseline settings=default disk=true maxage=6h
jcmd <pid> JFR.check
```

Use when: the issue depends on time-based evidence such as CPU, allocation, I/O, or lock behavior.

Startup-attached JFR:

```bash
java -XX:StartFlightRecording=name=startup,settings=profile,filename=/path/to/private-diagnostics/startup.jfr,dumponexit=true -jar app.jar
```

Use when: the problem happens during startup, very early request handling, or any phase that might be missed if JFR starts later via `jcmd`.

> [!IMPORTANT]
> JFR recordings can contain stack traces, class names, request metadata, and other sensitive runtime details. Prefer a private diagnostics directory with restrictive permissions instead of a shared location such as `/tmp`, and clean up captures promptly after analysis.
>
> For JDK 8, treat JFR as a special-case workflow, not the default path. Verify that the target runtime and operational policy actually permit Flight Recorder before recommending these commands.

Heap-oriented escalation:

```bash
jcmd <pid> GC.heap_info
jcmd <pid> GC.class_histogram
```

> [!NOTE]
> `GC.class_stats` was removed in JDK 15 and should not be treated as a current default diagnostic command. Use `GC.class_histogram` for heap class analysis on modern JVMs.

Use when: the symptom is memory growth, allocation pressure, or suspected heap retention.

Native-memory escalation:

```bash
jcmd <pid> VM.native_memory summary
```

Use when: RSS or container memory keeps growing but heap evidence alone does not explain the pressure.

Important setup note:

- Native Memory Tracking must be enabled at JVM startup with `-XX:NativeMemoryTracking=summary` or `-XX:NativeMemoryTracking=detail`

Legacy `jstack` thread dump:

```bash
jstack -l <pid>
```

Use when: you need the traditional `jstack` output shape or are working in an older operational workflow that still documents `jstack`.

Legacy `jmap` histogram and dump:

```bash
jmap -histo <pid>
jmap -dump:live,format=b,file=heap.hprof <pid>
```

Use when: you specifically need the standalone `jmap` form instead of the newer `jcmd` command family.

> [!WARNING]
> Heap dumps are highly sensitive artifacts and can contain credentials, tokens, session state, and PII. Write them only to restricted paths, transfer them over approved secure channels, and delete them as soon as the investigation allows.

Postmortem `jhsdb` core analysis:

```bash
jhsdb jstack --exe "$JAVA_HOME/bin/java" --core /path/to/core
jhsdb jmap --exe "$JAVA_HOME/bin/java" --core /path/to/core --heap
```

Use when: the JVM has already crashed or you need core-file inspection rather than routine live attach.

## Validate the Result

Validate the common path with these checks:

```bash
jcmd <pid> help Thread.print
jcmd <pid> JFR.check
```

- `Thread.print -l` completes and produces thread state plus lock detail.
- `JFR.check` confirms the recording state before claiming JFR is running.
- `GC.class_histogram` reflects the expected process, not the wrong PID.
- `VM.native_memory` confirmed Native Memory Tracking was enabled at startup; comparing like-for-like captures.

Tool-choice checks (not validation commands, but decision rationale to confirm before deeper escalation):

- `jstack` or `jmap` chosen with a specific reason not to use the `jcmd` equivalent.
- `jhsdb` chosen for postmortem, core-based, or explicitly SA-oriented case, not normal live-process triage.

## Format-Critical Output Shapes

### `jcmd -l` Output (JVM Discovery)

```text
12345 com.example.App /opt/app/app.jar
```

Read: PID = 12345, main class = `com.example.App`, launch path = `/opt/app/app.jar`. Use this PID for all subsequent `jcmd <pid>` commands.

If no JVMs appear, either no JVM is running, or the current user/namespace cannot see the target process.

### `Thread.print -l` Thread Dump Shape

Each thread block follows this structure:

```text
"http-nio-8080-exec-42" #284 daemon prio=5 os_prio=0 cpu=120.00ms elapsed=320.50s tid=0x00007f9a2c01e800 nid=0x4e03 waiting on condition [0x00007f99c4bfe000]
   java.lang.Thread.State: TIMED_WAITING (parking)
    at sun.misc.Unsafe.park(Native Method)
    - parking to wait for  <0x00000006f1a9d040> (a java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject)
    at java.util.concurrent.locks.LockSupport.parkNanos(LockSupport.java:257)
    at java.util.concurrent.SynchronousQueue$TransferStack.awaitFulfill(SynchronousQueue.java:453)
    at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1065)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1127)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
    at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:61)
    at java.lang.Thread.run(Thread.java:840)
   Locked ownable synchronizers:
    - None
```

Key fields per thread:

| Field | Location | How to read it |
| --- | --- | --- |
| Thread name | `"http-nio-8080-exec-42"` | Application thread naming convention; pool threads show pattern |
| Thread state | `java.lang.Thread.State: TIMED_WAITING (parking)` | See state table below |
| CPU time | `cpu=120.00ms` | Total CPU consumed by this thread since start |
| Elapsed time | `elapsed=320.50s` | Wall-clock time since thread started |
| Native ID | `nid=0x4e03` | OS-level thread ID for `top -H` or `strace -p` correlation |
| Stack trace | indented lines below state | Most recent call first; native frames show `(Native Method)` |
| Lock info | `Locked ownable synchronizers:` | `- None` means no `ReentrantLock` held |

Thread states and what they mean:

| State | Normal when... | Concern when... |
| --- | --- | --- |
| `RUNNABLE` | Thread is actively executing CPU work | Many threads RUNNABLE + high CPU = saturation |
| `BLOCKED` | Waiting to enter a `synchronized` block | Same lock contested by many threads = contention |
| `TIMED_WAITING` | Sleeping, parked with timeout (`Thread.sleep`, `parkNanos`) | Stuck in TIMED_WAITING indefinitely = hung operation |
| `WAITING` | Waiting indefinitely (`Object.wait()`, `LockSupport.park`) | Never recovers = deadlock or missed signal |
| `NEW` / `TERMINATED` | Thread not yet started or already finished | Large counts of NEW threads = thread leak |

### `GC.heap_info` Output

```text
Min heap alignment: 4096 KiB
G1 Heap:
   num_regions: 512
   Heap size: 1048576K
   Free regions: 200
   Young regions: 180
   Eden regions: 160
   Survivor regions: 20
   Old regions: 132
```

Read: Total heap size vs free/young/old region distribution. If `Free regions` drops low during load, heap pressure exists.

### `GC.class_histogram` Output (Top Lines)

```text
num     #instances         #bytes  class name
----------------------------------------------
    1:          48000     15360000  [B
    2:          32000      8320000  [Ljava.lang.Object;
    3:          25000      6000000  com.example.model.Data
    4:           5000      2400000  java.util.concurrent.ConcurrentHashMap$Node
```

Read: rank → instance count → total bytes → class name. `[B` = byte arrays, `[L...;` = object arrays. Focus on top 5–10 classes by bytes; if a single application class dominates, investigate retention in that class.

### `VM.native_memory summary` Output

```text
Native Memory Tracking:

Total: reserved=1024MB, committed=512MB

- Java Heap (reserved=512MB, committed=256MB)
- Class (committed=64MB)
- Thread (committed=48MB # of 24 threads)
- Code (committed=32MB)
- GC (committed=96MB)
- Internal (committed=16MB)
- Symbol (committed=12MB)
- Native Memory Tracking (committed=8MB)
- Compiler (committed=24MB)
- Internationalization (committed=4MB)
```

Read: If total committed approaches container limit but Java Heap is small, non-heap categories (Code, GC, Thread, Compiler) may be the real pressure source. `# of N threads` shows live thread count.

### `JFR.check` Output

```text
Recording 1: name=gc-baseline maxsize=0 (unlimited) duration=0 (unlimited) disk=true
  Recording: to=true size=48 KiB maxsize=0 (unlimited) duration=2 h (2 h)
  Dump on exit: false
  Path: /tmp/gc-baseline_12345_2026-04-20_101530.jfr
```

Read: Confirm `to=true` (recording active), check `size` growth rate, verify `duration` matches your `maxage` setting.

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| deciding which `jcmd` command family to use on a live JVM | `./references/jcmd-commands.md` |
| deciding whether JDK 8-era `jstack` or `jmap` guidance is still justified | `./references/jdk8-legacy-tools.md` |
| capturing repeated thread dumps, starting JFR, or deciding between snapshot and time-based evidence | `./references/thread-dumps-jfr.md` |
| using Serviceability Agent tools such as `jhsdb` for core files or deeper attach workflows | `./references/jhsdb.md` |

## Invariants

- MUST start from currently available evidence such as stack traces, logs, or previous captures.
- MUST prefer `jcmd` before legacy `jstack` or `jmap` for common runtime diagnostics.
- MUST reserve `jhsdb` for cases that truly need Serviceability Agent behavior.
- MUST choose the smallest next tool that reduces uncertainty.
- MUST distinguish evidence from guesswork.
- SHOULD prefer low-risk evidence collection first.
- MUST explain what each command reveals before suggesting it.
- MUST NOT recommend heap dumps or deep profiling unless the symptom justifies the operational cost.
- SHOULD check native-memory evidence before blaming all memory growth on the Java heap.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| jumping straight to a heap dump | high cost and often unnecessary for the first pass | start with `Thread.print`, `GC.heap_info`, or JFR |
| reading one thread dump as a full story | one snapshot can hide transient blocking or scheduler effects | capture repeated dumps and compare them |
| using `jstack` or `jmap` by default | current JDK guidance favors `jcmd` for common attach operations | prefer `jcmd <pid> help` and then the specific subcommand |
| using `jhsdb` for ordinary live triage | Serviceability Agent attach is heavier and official docs warn about hangs/crashes on detach | keep `jhsdb` for core dumps or SA-specific workflows |
| assuming RSS growth must be heap growth | non-heap memory such as metaspace, code cache, arenas, or thread stacks can dominate | use `VM.native_memory` when the heap story does not fit |
| collecting evidence without naming the symptom | tool choice becomes random | classify the symptom first, then choose the smallest matching capture |

## Scope Boundaries

- Activate this skill for:
  - stack trace and thread-dump interpretation
  - choosing the next JVM runtime diagnostic command
  - low-risk runtime evidence collection with `jcmd` and JFR
- Do not use this skill as the primary source for:
  - GC collector selection or GC logging strategy
  - Java language design or test-structure decisions
  - general JDK packaging and module workflows
