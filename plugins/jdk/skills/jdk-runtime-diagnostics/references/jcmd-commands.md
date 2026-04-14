---
title: jcmd Command Reference
description: >-
  Reference for choosing and invoking jcmd command families during live JVM diagnostics.
---

Use this reference when the runtime symptom is known and the remaining question is which `jcmd` command family should be used.

Base discovery:

```bash
jcmd -l
jcmd <pid> help
jcmd <pid> help Thread.print
```

Core VM identity and flags:

```bash
jcmd <pid> VM.version
jcmd <pid> VM.command_line
jcmd <pid> VM.flags
jcmd <pid> VM.flags -all
```

Thread inspection:

```bash
jcmd <pid> Thread.print
jcmd <pid> Thread.print -l
jcmd <pid> Thread.print -e
```

- use `Thread.print -l` as the standard diagnostic default because it includes ownable synchronizers and lock detail
- use `-e` only when extended thread information is worth the extra output

Heap and object inspection:

```bash
jcmd <pid> GC.heap_info
jcmd <pid> GC.class_histogram
jcmd <pid> GC.class_histogram -all
jcmd <pid> GC.heap_dump heap.hprof
```

Version-sensitive note:

- newer JDKs document extra `GC.class_histogram` / `GC.heap_dump` options such as parallelism, overwrite, or gzip-related controls
- use `jcmd <pid> help GC.heap_dump` and `jcmd <pid> help GC.class_histogram` on the target runtime before assuming they exist

> [!WARNING]
> `GC.class_stats` was removed in JDK 15. Do not treat it as a modern default command.

Native memory inspection:

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

Operational notes:

- `jcmd` must run on the same machine as the target JVM
- effective user/group must match the target process
- attach-based operation can be blocked by `-XX:+DisableAttachMechanism`
- `jcmd <pid> help <command>` is the authoritative syntax source for that exact runtime
- container and PID-namespace boundaries still matter; `jcmd -l` only shows JVMs visible from the current namespace and user context
