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

JDK 8 removed the PermGen space and moved class metadata into native-memory Metaspace. Both generations of flag still appear in practice:

- `-XX:MaxPermSize=<n>` applies only to JDK 7 and earlier. On JDK 8 it is accepted but produces a warning and is then ignored. On JDK 9 and later the flag is unrecognized and the JVM refuses to start with `Unrecognized VM option 'MaxPermSize=<n>'`, so any legacy startup scripts still carrying it MUST be cleaned up as part of the upgrade.
- `-XX:MetaspaceSize=<n>` sets the initial Metaspace high-water mark (the first level at which an unloading-triggered GC runs). Setting this low wastes GC cycles; setting it close to the expected steady-state metaspace size avoids early GC pressure. There is no implicit default from heap size.
- `-XX:MaxMetaspaceSize=<n>` caps Metaspace; the default is effectively unlimited, so a JVM with a class-loader leak MAY exhaust host memory before a native OOM surfaces. Set an explicit cap in production.
- `-XX:CompressedClassSpaceSize=<n>` bounds the compressed-class-pointers region that lives inside Metaspace on 64-bit JVMs when compressed oops/class pointers are enabled; it is distinct from `MaxMetaspaceSize`.

Error strings to distinguish:

- `OutOfMemoryError: PermGen space` means the runtime is JDK 7 or earlier. If this appears on a "JDK 8" process, the deployed runtime is not actually JDK 8.
- `OutOfMemoryError: Metaspace` means Metaspace is exhausted on JDK 8+. Raise `-XX:MaxMetaspaceSize` or investigate dynamic class generation and class-loader retention.
- `OutOfMemoryError: Compressed class space` means the compressed-class region is exhausted; raise `-XX:CompressedClassSpaceSize` or `-XX:MaxMetaspaceSize`, or disable compressed class pointers.

Diagnostic output:

- `jmap -heap <pid>` shows Metaspace on JDK 8 and later; it shows PermGen only on JDK 7 and earlier.
- `jcmd <pid> VM.native_memory` (with NMT enabled at startup) reports Metaspace and Compressed Class Space consumption separately.

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
