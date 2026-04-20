---
title: jcmd Command Reference
description: >-
  Reference for extended jcmd command families, version-sensitive options, and operational
  constraints beyond the common-case commands in the parent SKILL.md.
---

Use this reference when the base discovery and core VM identity commands from the parent SKILL.md are not sufficient and the remaining blocker is which extended `jcmd` variant or option to apply.

## Extended Thread Inspection

```bash
jcmd <pid> Thread.print -e
```

- use `Thread.print -l` (documented in parent SKILL.md) as the standard diagnostic default because it includes ownable synchronizers and lock detail
- use `-e` only when extended thread information is worth the extra output

## Extended Heap and Object Inspection

```bash
jcmd <pid> GC.class_histogram -all
jcmd <pid> GC.heap_dump heap.hprof
```

> [!WARNING]
> Heap dumps are highly sensitive artifacts. Use a restricted destination path, handle transfer and retention as sensitive-data operations, and prefer histograms or JFR first when they can answer the question with lower disclosure risk.

Version-sensitive note:

- newer JDKs document extra `GC.class_histogram` / `GC.heap_dump` options such as parallelism, overwrite, or gzip-related controls
- use `jcmd <pid> help GC.heap_dump` and `jcmd <pid> help GC.class_histogram` on the target runtime before assuming they exist

> [!WARNING]
> `GC.class_stats` was removed in JDK 15. Do not treat it as a modern default command.

## Native Memory Inspection

```bash
jcmd <pid> VM.native_memory summary
jcmd <pid> VM.native_memory detail
jcmd <pid> VM.native_memory baseline
jcmd <pid> VM.native_memory summary.diff
```

> [!IMPORTANT]
> Native Memory Tracking must be enabled at JVM startup with `-XX:NativeMemoryTracking=summary` or `-XX:NativeMemoryTracking=detail`.

- use `summary` for the lowest-noise first look at non-heap memory categories
- use `detail` only when allocator-level evidence is worth the extra output
- use `baseline` and `summary.diff` for repeated captures that isolate growth over time

## Operational Constraints

- `jcmd` must run on the same machine as the target JVM
- effective user/group must match the target process
- attach-based operation can be blocked by `-XX:+DisableAttachMechanism`
- `jcmd <pid> help <command>` is the authoritative syntax source for that exact runtime
- container and PID-namespace boundaries still matter; `jcmd -l` only shows JVMs visible from the current namespace and user context
