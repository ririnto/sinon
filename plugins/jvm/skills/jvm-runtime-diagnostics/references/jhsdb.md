---
title: jhsdb Reference
description: >-
  Reference for using jhsdb and its Serviceability Agent subcommands during postmortem or deeper JVM runtime diagnostics.
---

Use this reference when normal live-process `jcmd` diagnostics are not enough and the case requires Serviceability Agent tooling such as core-dump analysis, SA debugger modes, or SA-style inspection.

## What jhsdb Is For

`jhsdb` is the HotSpot Serviceability Agent front end. Prefer it for:

- crashed JVM core files
- `--exe` + `--core` analysis
- SA debugger workflows
- remote SA debug-server flows

Do not make it the default for normal live-process triage. Official docs warn that attaching `jhsdb` to a live process can hang the process and may crash it when the debugger detaches.

## Common Invocation Forms

Live process:

```bash
jhsdb <subcommand> --pid <pid>
```

Core file:

```bash
jhsdb <subcommand> --exe "$JAVA_HOME/bin/java" --core /path/to/core
```

Remote SA debug server:

```bash
jhsdb debugd --pid <pid>
jhsdb <subcommand> --connect host
```

> [!WARNING]
> Remote SA debug-server flows are admin-only diagnostics. Keep them on trusted networks or behind SSH tunneling, never expose them to the public internet, and shut them down immediately after the investigation.

## jhsdb jstack

Typical shapes:

```bash
jhsdb jstack --pid <pid>
jhsdb jstack --pid <pid> --locks
jhsdb jstack --pid <pid> --mixed
jhsdb jstack --exe "$JAVA_HOME/bin/java" --core /path/to/core
```

Use:

- `--locks` for `java.util.concurrent` lock information
- `--mixed` when native plus Java frame detail matters
- `--exe` + `--core` for postmortem stack analysis

## jhsdb jmap

Typical shapes:

```bash
jhsdb jmap --pid <pid> --heap
jhsdb jmap --pid <pid> --histo
jhsdb jmap --pid <pid> --clstats
jhsdb jmap --pid <pid> --finalizerinfo
jhsdb jmap --pid <pid> --binaryheap --dumpfile heap.hprof
```

Use:

- `--heap` for heap layout summary
- `--histo` for class histogram
- `--clstats` for class-loader statistics
- `--finalizerinfo` for finalizer queue inspection
- `--binaryheap --dumpfile` for HPROF heap dump output

## jhsdb jinfo

Typical shapes:

```bash
jhsdb jinfo --pid <pid> --flags
jhsdb jinfo --pid <pid> --sysprops
```

Use:

- `--flags` to inspect JVM flags
- `--sysprops` to inspect Java system properties

## jhsdb jsnap

Typical shapes:

```bash
jhsdb jsnap --pid <pid>
jhsdb jsnap --pid <pid> --all
```

Use:

- default output for common performance counters
- `--all` when the narrower default set is not enough

## jhsdb clhsdb and hsdb

Typical shapes:

```bash
jhsdb clhsdb --pid <pid>
jhsdb hsdb --pid <pid>
```

Use:

- `clhsdb` for command-line SA debugger workflows
- `hsdb` for GUI-based SA inspection when a graphical environment is available

## When to Prefer jhsdb

Prefer `jhsdb` when:

- the JVM already crashed and you have a core file
- you need `--exe` + `--core` workflows
- you need SA debugger modes or SA remote debug server behavior

## Operational Caveats

- Oracle docs mark `jhsdb` as experimental/unsupported
- live attach can hang the target process and may crash it on detach
- same-machine, executable-path, and symbol expectations still matter for good results
- use the exact matching `java` executable for `--exe` when reading a core file
- treat heap dumps, system properties, and remote SA channels as sensitive operational data paths
