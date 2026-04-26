---
title: Collector Baselines
description: >-
  Reference for JDK LTS collector availability, default assignments, version-specific notes,
  and collector-selection decision guidance.
---

Use this reference when the blocker is confirming which collectors are available, default, or realistic on a specific Java LTS baseline, or when comparing collector tradeoffs across versions.

## Collector Availability by LTS

| Java baseline | Default collector | Selectable collectors | Key flags |
| --- | --- | --- | --- |
| 8 | Parallel (server-class) | Serial, Parallel, CMS, G1 | `-XX:+UseSerialGC`, `-XX:+UseParallelGC`, `-XX:+UseConcMarkSweepGC`, `-XX:+UseG1GC` |
| 11 | G1 | Serial, Parallel, G1, ZGC (experimental) | `-XX:+UnlockExperimentalVMOptions -XX:+UseZGC` |
| 17 | G1 | Serial, Parallel, G1, ZGC | `-XX:+UseZGC` |
| 21 | G1 | Serial, Parallel, G1, ZGC (+ optional generational) | `-XX:+UseZGC -XX:+ZGenerational` |
| 25 | G1 | Serial, Parallel, G1, ZGC (generational-only) | `-XX:+UseZGC` |

## Shenandoah Availability

Shenandoah is not a default HotSpot collector on any LTS baseline. Its availability depends on the JDK vendor and build:

- **OpenJDK builds**: included in many distributions but NOT in Oracle JDK
- **Amazon Corretto**: ships Shenandoah
- **Azul Zulu**: ships Shenandoah on some configurations
- **Eclipse Temurin**: includes Shenandoah
- **Oracle JDK**: does NOT ship Shenandoah

Always confirm the actual distribution before recommending Shenandoah. Check with:

```bash
java -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlagsFinal -version 2>&1 | grep UseShenandoahGC
```

If the flag is not present, Shenandoah is not available in that build.

## Version-Specific Notes

### JDK 8

- CMS (`ConcurrentMarkSweep`) is available on JDK 8 and selected with `-XX:+UseConcMarkSweepGC`. It was deprecated in JDK 9 (JEP 291) and removed in JDK 14 (JEP 363), so any CMS advice applies only to JDK 8-era runtimes.
- Parallel is the default for server-class JVMs; Serial for client-class.
- No unified logging: use `-verbose:gc`, `-XX:+PrintGCDetails`, `-XX:+PrintGCTimeStamps`, `-Xloggc:gc.log`.
- JFR requires Oracle JDK commercial features license verification.

### JDK 11

- G1 becomes the default collector.
- ZGC exists but requires `-XX:+UnlockExperimentalVMOptions -XX:+UseZGC`.
- Unified logging (`-Xlog`) replaces all legacy GC log flags.
- JFR is available without commercial-feature unlock on OpenJDK 11.

### JDK 17

- ZGC is stable (no experimental unlock needed).
- CMS removed entirely.
- Generational ZGC does not exist yet; ZGC is non-generational only.

### JDK 21

- ZGC supports optional generational mode via `-XX:+ZGenerational`.
- The explicit generational shape is `-XX:+UseZGC -XX:+ZGenerational`.
- Non-generational ZGC remains available (omit `-XX:+ZGenerational`).
- `-XX:+ZGenerational` is still required on this LTS line to opt into generational mode.

### JDK 22 (non-LTS, transition release)

- `-XX:+ZGenerational` retains JDK 21 opt-in semantics: the default remains non-generational, so a launch without the flag runs the non-generational collector.
- No deprecation warning is printed yet; the flag is fully supported and not yet deprecated.
- Use this LTS boundary note only when triaging a short-lived JDK 22 deployment; treat JDK 23 as the transition point that flips the default.

### JDK 23 (non-LTS, transition release)

- JEP 474 flipped the default: generational mode becomes the default behavior of ZGC and `-XX:+ZGenerational` is deprecated. A launch that does not specify the flag now runs generational ZGC.
- Passing `-XX:+ZGenerational` still selects generational mode (matching the new default); passing `-XX:-ZGenerational` selects the deprecated non-generational mode and produces a deprecation warning.

### JDK 24 (non-LTS, removal release)

- JEP 490 removes the non-generational code and obsoletes the `ZGenerational` option.
- Passing `-XX:+ZGenerational` is accepted but produces a warning like `Java HotSpot(TM) 64-Bit Server VM warning: Ignoring option ZGenerational; support was removed in 24.0` and is ignored.
- Only generational ZGC is available; there is no supported path back to the non-generational algorithm.

### JDK 25

- ZGC is generational-only; `-XX:+UseZGC` alone implies generational.
- JEP 490 (JDK 24) obsoleted `-XX:+ZGenerational` and removed the non-generational mode. On JDK 24 and JDK 25 the flag is still accepted at startup but produces a warning similar to `Java HotSpot(TM) 64-Bit Server VM warning: Ignoring option ZGenerational; support was removed in 24.0`, and the option is then ignored. Do not rely on it for any collector selection.
- Per the OpenJDK tracker, `ZGenerational` is scheduled to expire in JDK 26 (refuse-to-start). Treat any JDK 25 script or runbook that still passes `-XX:+ZGenerational` as a future break waiting to happen, even though startup succeeds today.
- There is no way to select non-generational ZGC on this baseline.
- Shenandoah availability remains vendor-dependent as in earlier releases.

## Collector Selection Decision Tree

```text
Symptom identified as GC-driven?
├── No → switch to non-GC runtime triage and collect thread, CPU, I/O, or application evidence
├── Yes → What is the primary goal?
│   ├── Lowest pause time → consider ZGC or Shenandoah
│   ├── Maximum throughput → Parallel or G1 (default)
│   ├── Smallest footprint → Serial or G1 with small heap
│   └── Balanced (default) → stay with G1 unless evidence shows mismatch
└── Before switching:
    1. Confirm current LTS baseline
    2. Read GC logs / JFR evidence
    3. Verify target collector exists in the deployed JDK
    4. Frame recommendation in workload terms (pause ms, throughput %, heap GB)
```

## Common Collector Flag Patterns

G1 tuning entry points:

```bash
-XX:MaxGCPauseMillis=200
-Xms2g -Xmx4g
-XX:+DisableExplicitGC
```

- `MaxGCPauseMillis` sets the GC pause time target (default 200ms)
- `-Xms` / `-Xmx` set initial and max heap
- `DisableExplicitGC` ignores `System.gc()` calls (use cautiously)

ZGC tuning entry points:

```bash
-Xms4g -Xmx4g
-XX:+UseZGC
-XX:SoftMaxHeapSize=3g
```

- ZGC performs well with larger heaps; set `-Xms` equal to `-Xmx` for stability.
- `-XX:+ZGenerational` is the JDK 21 opt-in flag for generational ZGC. On JDK 22 the option is still accepted with its original opt-in semantics, so a JDK 22 launch that does not pass `-XX:+ZGenerational` runs the non-generational mode. JEP 474 (JDK 23) made generational mode the default and deprecated the option. JEP 490 (JDK 24) obsoleted the option and removed the non-generational code, so on JDK 24 and JDK 25 passing the flag logs a warning and is ignored. JDK 26 is scheduled to expire the option, at which point the JVM will refuse to start if it is specified. Selecting ZGC with `-XX:+UseZGC` alone is the forward-compatible form on all versions from JDK 21 onward; on JDK 21 and JDK 22 add `-XX:+ZGenerational` only when generational mode is explicitly required.
- `SoftMaxHeapSize` sets a soft heap limit (JDK 21+).

Parallel GC tuning entry points:

```bash
-XX:ParallelGCThreads=4
-XX:NewSize=512m -XX:MaxNewSize=1g
```

- `ParallelGCThreads` sets the thread count for parallel GC
- `NewSize` / `MaxNewSize` control young generation size
