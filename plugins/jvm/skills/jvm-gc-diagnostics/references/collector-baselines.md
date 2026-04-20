---
title: Collector Baselines
description: >-
  Reference for JDK LTS collector availability, default assignments, version-specific notes,
  and collector-selection decision guidance.
---

Use this reference when the blocker is confirming which collectors are available, default, or realistic on a specific Java LTS baseline, or when comparing collector tradeoffs across versions.

## Collector Availability by LTS

| Java baseline | Default collector | Selectable collectors | Key flags |
| --- | --- | | --- |
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

- CMS (`ConcurrentMarkSweep`) is available but deprecated (removed in JDK 9).
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

### JDK 25

- ZGC is generational-only; `-XX:+UseZGC` alone implies generational.
- `-XX:+ZGenerational` was obsoleted in JDK 24 (JEP 490) and removed from JDK 25. Using it on JDK 25 causes an unrecognized-flag error.
- There is no way to select non-generational ZGC on this baseline.
- Shenandoah availability remains vendor-dependent as in earlier releases.

## Collector Selection Decision Tree

```text
Symptom identified as GC-driven?
├── No → use jvm-runtime-diagnostics instead
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
-XX:+UseZGC -XX:+ZGenerational
-XX:SoftMaxHeapSize=3g
```

- ZGC performs well with larger heaps; set `-Xms` equal to `-Xmx` for stability
- `-XX:+ZGenerational` enables generational mode (JDK 21 only; removed in JDK 24+)
- `SoftMaxHeapSize` sets a soft heap limit (JDK 21+)

Parallel GC tuning entry points:

```bash
-XX:ParallelGCThreads=4
-XX:NewSize=512m -XX:MaxNewSize=1g
```

- `ParallelGCThreads` sets the thread count for parallel GC
- `NewSize` / `MaxNewSize` control young generation size
