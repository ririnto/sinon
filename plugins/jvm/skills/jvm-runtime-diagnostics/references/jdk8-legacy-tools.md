---
title: JDK 8-Era Legacy Tools Reference
description: >-
  Reference for JDK 8-era and older standalone jstack and jmap workflows, version-specific
  flags, and operational caveats when jcmd is not the chosen path.
---

Use this reference when the target workflow explicitly requires classic JDK 8-era `jstack` or `jmap` command shapes instead of the current `jcmd` path, or when operating on a JDK 8 runtime where version-specific flags and constraints apply.

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

## JDK 8-Specific Operational Caveats

### PermGen vs Metaspace

JDK 8 moved class metadata storage from PermGen (`-XX:MaxPermSize`) to native Metaspace (`-XX:MaxMetaspaceSize`). When diagnosing JDK 8 memory issues:

- `OutOfMemoryError: PermGen space` indicates the legacy PermGen area is exhausted; increase `-XX:MaxPermSize` or investigate class-loader leaks
- `OutOfMemoryError: Metaspace` indicates native Metaspace is exhausted; increase `-XX:MaxMetaspaceSize` or investigate dynamic class generation
- `jmap -heap <pid>` output shows PermGen usage on JDK 8 but Metaspace on JDK 9+
- `jcmd <pid> VM.native_memory` can show metaspace consumption when NMT is enabled

### JDK 8 JFR Availability

On Oracle JDK 8, Flight Recorder is a commercial feature:

```bash
java -XX:+UnlockCommercialFeatures -XX:+FlightRecorder ...
```

- OpenJDK 8 builds may not include JFR at all; check with `jcmd <pid> JFR.start` (if the command is not listed in `jcmd <pid> help`, JFR is unavailable)
- Oracle JDK 8 Update releases vary in JFR capability; verify the exact update level
- On JDK 11 and later, JFR is available without commercial-feature unlock on all OpenJDK builds

### JDK 8 GC Logging Flags

JDK 8 uses legacy GC logging flags, not unified logging:

```bash
java -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+PrintGCTenuringDistribution -Xloggc:gc.log ...
```

- `-XX:+PrintGCDateStamps` adds ISO datestamps to each line
- `-XX:+PrintGCTenuringDistribution` shows survivor age distribution for generational tuning
- There is no `-Xlog` support on JDK 8; all GC logging must use the legacy flags above

### Diagnostic Flags Removed After JDK 8

Several diagnostic flags available on JDK 8 were removed or replaced in later releases:

- `-XX:+PrintCommandLineFlags` still works on all LTS versions
- `-XX:+PrintGCApplicationStoppedTime` (JDK 8) → `-Xlog:safepoint` (JDK 9+)
- `-XX:+PrintSafepointStatistics` (JDK 8) → `-Xlog:safepoint+stats` (JDK 9+)
- `-XX:MaxPermSize` (JDK 8 only) → replaced by `-XX:MaxMetaspaceSize`
