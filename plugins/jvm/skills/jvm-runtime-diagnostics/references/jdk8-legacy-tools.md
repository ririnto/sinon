---
title: JDK 8-Era Legacy Tools Reference
description: >-
  Reference for JDK 8-era and older standalone jstack and jmap workflows when jcmd is not the chosen path.
---

Use this reference when the target workflow explicitly requires classic JDK 8-era `jstack` or `jmap` command shapes instead of the current `jcmd` path.

## jstack

Typical shapes:

```bash
jstack <pid>
jstack -l <pid>
```

Legacy/older-doc shapes:

```bash
jstack -m <pid>
jstack -F <pid>
```

- use `-l` to include ownable synchronizer information
- use `-m` for mixed Java/native frames in older documented workflows
- use `-F` only in older/legacy operational playbooks for unresponsive processes on supported platforms

## jmap

Typical shapes:

```bash
jmap -histo <pid>
jmap -histo:live <pid>
jmap -dump:live,format=b,file=heap.hprof <pid>
jmap -dump:format=b,file=heap.hprof <pid>
jmap -clstats <pid>
jmap -finalizerinfo <pid>
```

Legacy/older-doc shapes:

```bash
jmap -heap <pid>
jmap -F -histo <pid>
jmap -F -dump:format=b,file=heap.hprof <pid>
```

- current JDK docs mark `jstack` and `jmap` as experimental or unsupported
- prefer `jcmd Thread.print`, `GC.class_histogram`, and `GC.heap_dump` unless the classic standalone form is specifically required
- heap dumps can be very high impact on large heaps
