---
name: jdk-runtime-diagnostics
description: >-
  This skill should be used when the user asks to "debug a JVM issue", "read a Java stack trace", "analyze thread dumps", "use jcmd or JFR", "diagnose memory pressure", or needs guidance on JDK runtime incident triage.
---

# JDK Runtime Diagnostics

## Overview

Use this skill to triage JVM runtime problems with standard JDK diagnostic tools and an evidence-first workflow. The common case is not "guess the root cause" but "collect the smallest next capture that reduces uncertainty". Prefer `jcmd` first for live JVMs, keep `jstack` and `jmap` as legacy or narrower-purpose tools, and reserve `jhsdb` for Serviceability Agent cases such as core dumps or deeper postmortem inspection.

## Use This Skill When

- You have a JVM symptom such as latency, startup failure, heap or native-memory pressure, lock contention, or unexplained slowness.
- You need to decide between `jcmd`, `jstack`, `jmap`, `jhsdb`, and JFR.
- You need the first production-safe command to run against a live JVM.
- Do not use this skill when the main task is choosing a garbage collector or tuning GC logging policy rather than collecting general runtime evidence.

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
jcmd
```

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
jcmd
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

Low-overhead JFR start for a running JVM:

```bash
jcmd <pid> JFR.start name=baseline settings=default disk=true maxage=6h
jcmd <pid> JFR.check
```
Use when: the issue depends on time-based evidence such as CPU, allocation, I/O, or lock behavior.

Startup-attached JFR:

```bash
java -XX:StartFlightRecording=name=startup,settings=profile,filename=/tmp/startup.jfr,dumponexit=true -jar app.jar
```
Use when: the problem happens during startup, very early request handling, or any phase that might be missed if JFR starts later via `jcmd`.

Heap-oriented escalation:

```bash
jcmd <pid> GC.heap_info
jcmd <pid> GC.class_histogram
```
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
- `jstack` or `jmap` chosen with a specific reason not to use the `jcmd` equivalent.
- `jhsdb` chosen for postmortem, core-based, or explicitly SA-oriented case, not normal live-process triage.

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
