---
metadata:
  reference:
    jcmd:
      version: JDK 25
      url: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html
    jhsdb:
      version: JDK 25
      url: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jhsdb.html
name: jvm-runtime-diagnostics
description: >-
  Diagnose JVM incidents from stack traces, thread dumps, JFR, jcmd output, or memory-pressure evidence.
---

# JVM Runtime Diagnostics

## Goal

Triage JVM runtime problems with standard JDK diagnostic tools and an evidence-first workflow.
Collect the smallest next capture that reduces uncertainty before naming a root cause.
Prefer `jcmd` first for live JVMs, keep `jstack` and `jmap` as legacy or narrower-purpose tools, and reserve `jhsdb` for Serviceability Agent cases such as core dumps or deeper postmortem inspection.

Treat JDK 8, 11, 17, 21, and 25 as the supported LTS reference line for this skill, and confirm runtime-specific command availability on the target JVM before assuming a newer flag or event exists.

Treat JFR as the standard low-overhead path on JDK 11 and later.
On JDK 8, do not assume JFR is ordinarily available: verify the exact Oracle JDK 8 commercial-feature and licensing posture before recommending `JFR.start` or `-XX:StartFlightRecording`, and prefer thread dumps plus other low-risk captures when that requirement is not clearly satisfied.

## Task Context

Start from the available traces, logs, dumps, or recordings and identify the symptom that needs explanation.
Use [jcmd commands](./references/jcmd-commands.md) for live diagnostic command selection.
Use [thread dumps and JFR](./references/thread-dumps-jfr.md) for snapshot comparison or time-based evidence.
Use [legacy tools](./references/jdk8-legacy-tools.md) only for a JDK 8-era tool requirement.
Use [jhsdb](./references/jhsdb.md) for core files or Serviceability Agent work.

## Diagnostic Authority

Confirm the target process, allowed capture scope, and command impact before live attachment.
Continue authorized diagnostics without requesting approval for each command.
Heap dumps, live Serviceability Agent attachment, remote debug servers, and runtime changes need authority covering their specific impact.
When that authority is missing, analyze existing evidence and report the precise next capture needed.
Store diagnostic captures in restricted paths and remove them when the investigation allows.
Stop recordings started for the investigation when finished; leave others untouched unless their owner authorizes a change.

## Minimal Setup

Run diagnostics on the same machine as the target JVM and, for attach-based tools, with the same effective user/group as the target process.

Tool-selection baseline:

- `jcmd` for normal live-process diagnostics
- `jstack` and `jmap` only when their narrower legacy output shape is specifically useful
- `jhsdb` for core dumps, SA debugger flows, or when live attach is not the normal `jcmd` path

Identify the JVM first:

```sh
jcmd -l
```

> [!NOTE]
>
> `jcmd -l` lists processes visible to the current user context.
> In container environments, process visibility may be restricted by namespace boundaries.
> Ensure you are querying from the correct user or namespace context.

## First Runnable Commands or Code Shape

Select the commands needed to identify the target and investigate the symptom:

```sh
jcmd -l
jcmd <pid> help
jcmd <pid> help Thread.print
jcmd <pid> VM.version
jcmd <pid> VM.command_line
jcmd <pid> VM.flags
jcmd <pid> Thread.print -l
```

Use when: the symptom is real but you do not yet know which deeper tool is justified.

Use [diagnostic capture templates](./references/diagnostic-capture-templates.md) when collecting thread, JFR, heap, native-memory, legacy-tool, or core-file evidence.

## Completion

Tie the diagnosis to the target process and captured evidence, and separate confirmed causes from hypotheses.
Validate only the capture used: thread detail, recording name and status, or enabled Native Memory Tracking, as applicable.
Explain any escalation beyond `jcmd` and any missing evidence that prevents a diagnosis.
Stop collecting when the existing evidence answers the operator’s question or further capture exceeds the grant.

Use [output shapes](./references/diagnostic-output-shapes.md) when interpreting process discovery, thread, heap, histogram, native-memory, or JFR output.

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
