---
name: jvm-gc-diagnostics
description: >-
  Analyze JVM garbage-collection behavior, compare collector tradeoffs, and interpret pause-time and heap-pressure evidence. Use this skill when the user asks to "analyze GC logs", "compare G1 and ZGC", "understand Java garbage collection", "debug pause times", "explain heap pressure", or needs guidance on JDK garbage-collection diagnostics and collector tradeoffs.
metadata:
  title: JVM GC Diagnostics
  official_project_url: "https://docs.oracle.com/en/java/"
  reference_doc_urls:
    - "https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/"
    - "https://docs.oracle.com/en/java/javase/11/gctuning/"
    - "https://docs.oracle.com/en/java/javase/17/gctuning/"
    - "https://docs.oracle.com/en/java/javase/21/gctuning/"
    - "https://docs.oracle.com/en/java/javase/25/gctuning/"
  version: "LTS"
---

## Overview

Use this skill to analyze garbage-collection symptoms, collector choices, and runtime evidence using standard JDK and HotSpot tooling. The common case is not switching collectors immediately; it is proving whether GC is actually the bottleneck, then comparing realistic collector options against the deployed Java baseline. Focus on pauses, throughput, allocation pressure, heap behavior, and what is actually available on the running JDK before touching tuning folklore.

Treat JDK 8, 11, 17, 21, and 25 as the supported LTS reference line for this skill, and anchor collector guidance to the actual deployed runtime instead of assuming the newest LTS behavior.

Treat JFR-based GC evidence as the normal low-overhead path on JDK 11 and later. On JDK 8, verify the exact Oracle JDK 8 Flight Recorder availability, commercial-feature status, and licensing posture before recommending JFR commands; otherwise prefer GC logs plus `jcmd` evidence first.

## Common-Case Workflow

1. Start from the symptom: pauses, throughput loss, allocation spikes, memory growth, or startup footprint.
2. Confirm the active collector and the deployed Java version or LTS baseline before discussing alternatives.
3. Gather or read GC evidence first with `jcmd`, GC logs, or JFR.
4. Compare collectors only after the evidence shows GC is the real bottleneck and the default or current collector is a mismatch.
5. Check vendor and build reality before recommending Shenandoah or other non-default collectors.

## Minimal Setup

For a running JVM, confirm the process and current runtime first:

```bash
jcmd -l
jcmd <pid> VM.version
jcmd <pid> VM.flags
```

For the next deploy, enable bounded GC log output for basic pause visibility:

```bash
java -Xlog:gc:file=gc-%p-%t.log:uptimemillis,pid:filecount=5,filesize=10M ...
```

Version-specific logging syntax — JDK 8 and earlier use legacy GC log flags (`-verbose:gc`, `-XX:+PrintGCDetails`, `-XX:+PrintGCTimeStamps`, `-Xloggc:gc.log`); JDK 9 and later use unified logging with `-Xlog:gc...`.

## First Runnable Commands or Code Shape

Start with the smallest GC evidence set:

```bash
jcmd -l
jcmd <pid> VM.flags
jcmd <pid> VM.command_line
jcmd <pid> GC.heap_info
jcmd <pid> JFR.start name=gc-baseline settings=default disk=true maxage=2h
```

Use when: you have a live JVM and need first-line GC evidence before discussing collectors.

## Ready-to-Adapt Templates

Live JVM GC first pass:

```bash
jcmd <pid> VM.version
jcmd <pid> VM.command_line
jcmd <pid> VM.flags
jcmd <pid> VM.flags -all
jcmd <pid> GC.heap_info
jcmd <pid> GC.class_histogram
```

Use when: the symptom is heap pressure, memory growth, or unexplained GC churn.

Low-overhead JFR GC recording:

```bash
jcmd <pid> JFR.start name=gc-baseline settings=default disk=true maxage=2h
jcmd <pid> JFR.check
jcmd <pid> JFR.dump name=gc-baseline filename=/path/to/private-diagnostics/gc-baseline-%p-%t.jfr
```

Use when: the pause or latency problem depends on runtime behavior over time.

Startup-attached GC-oriented JFR:

```bash
java -XX:StartFlightRecording=name=gc-startup,settings=profile,filename=/path/to/private-diagnostics/gc-startup.jfr,dumponexit=true -Xlog:gc=debug:file=gc-%p-%t.log:uptimemillis,pid:filecount=5,filesize=10M -jar app.jar
```

Use when: you need GC and allocation evidence from process start, not only after a later live attach.

> [!IMPORTANT]
> GC-oriented JFR captures and GC log files can still expose sensitive runtime details. Prefer a private diagnostics directory with restrictive permissions, and keep retention only as long as the investigation needs.
>
> On JDK 8, do not present JFR as the routine default. Confirm Oracle JDK 8 Flight Recorder availability and policy first, or stay with GC logs plus `jcmd` evidence when that is uncertain.

Bounded GC logging for the next deploy:

```bash
java -Xlog:gc=debug:file=gc-%p-%t.log:uptimemillis,pid:filecount=5,filesize=10M ...
```

Use when: you cannot diagnose the current issue from existing captures and need better next-run evidence.

Legacy JDK 8 GC logging:

```bash
java -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:gc.log ...
```

Use when: the deployed runtime is JDK 8 or earlier and `-Xlog` is not available.

Startup-time collector verification:

```bash
java -XX:+PrintCommandLineFlags -version
```

Use when: you are validating a launch configuration or checking whether a proposed collector flag is actually accepted.

Unified logging inspection:

```bash
jcmd <pid> VM.log list
```

Use when: you need to know whether GC logging is already active and at what level.

Collector comparison checklist:

```text
1. Confirm the active Java LTS baseline.
2. Confirm the active collector.
3. Decide whether the goal is pause time, throughput, or footprint.
4. Compare the default collector against alternatives only after reading evidence.
```

Use when: the user asks "Should we switch from G1 to ZGC?" or a similar collector-choice question.

## Validate the Result

Validate the common case with these checks:

- active collector known from runtime data, not assumption
- Java version/LTS boundary known before collector availability discussed
- operator knows whether runtime uses legacy or unified logging
- collector flags checked against the actual runtime era
- recommendation framed in pause/throughput/footprint, not collector branding
- no collector switch until GC evidence shows default is a mismatch

## Format-Critical Output Shapes

### `GC.heap_info` Output

```bash
jcmd <pid> GC.heap_info
```

Sample output (G1 on JDK 21):

```text
Min heap alignment: 4096 KiB
Reserved region:
 - [0x00000007ffc00000, 0x0000000800000000): committed
Heap region sizes:
 - Region 0: 2048 KiB
 - Region 1: 1024 KiB
G1 Heap:
   num_regions: 512
   Max regions: 512
   Heap size: 1048576K
   Free regions: 200
   Young regions: 180
   Eden regions: 160
   Survivor regions: 20
   Old regions: 132
   Humongous regions: 0
   Start CS time: 12345 ms
   End CS time: 12346 ms
```

Read: Total heap = 1048576K (~1GB), 200 free regions (39% free). Young gen = 180 regions (35%), Old gen = 132 regions (26%). If `Free regions` drops below ~50 during sustained load, the heap is under pressure.

### `VM.flags` Output (Collector Identity)

```bash
jcmd <pid> VM.flags
```

Look for these flag lines to identify the active collector:

```bash
-XX:G1HeapRegionSize=4M
-XX:+UseG1GC
-XX:+UseZGC
-XX:+UseParallelGC
-XX:+UseSerialGC
```

Read: `G1HeapRegionSize` or `UseG1GC` indicates G1, `UseZGC` indicates ZGC, `UseParallelGC` indicates Parallel GC, and `UseSerialGC` indicates Serial GC.

If none of these appear, the default collector for that LTS applies (G1 for 11+, Parallel for 8 server-class).

### `JFR.check` Output

```bash
jcmd <pid> JFR.check
```

Supported output cues to confirm:

```text
Recording 1: name=gc-baseline maxage=2 h (running)
```

Read: Match the recording `name`, confirm `(running)` before claiming the capture is active, and verify `maxage` matches the intended retention window. If the runtime also reports a destination or path, confirm it points to the restricted diagnostics location you intended.

### Unified GC Log Line Shape (JDK 9+)

```text
[0.952s][info][gc,start ] GC(0) Pause Young (Normal) (G1 Evacuation Pause)
[0.956s][info][gc,cpu  ] GC(0) User=0.01s Sys=0.00s Real=0.00s
```

Read each event: timestamp → log level → tag(s) → GC ID + phase → details. The `Real=` value in the `[gc,cpu]` line is the wall-clock pause duration.

### Legacy GC Log Line Shape (JDK 8)

```text
2026-04-20T10:15:30.123+0000: 1.234: [GC (Allocation Failure) ...] 196608K->139264K(524288K), 0.0234321 secs
```

Read: datestamp → uptime → GC type → total heap Before->After(Max) → pause seconds. The value before `secs` is the wall-clock pause duration.

### `jfr summary` Output

```bash
jfr summary /path/to/recording.jfr
```

Shows event counts grouped by type. Look for:

- `jdk.GarbageCollection` count — how many GC cycles occurred
- `jdk.GCPhasePause` — individual pause phases; check max duration
- `jdk.ObjectAllocationInNewTLAB` count — very high counts indicate allocation pressure

## References

| If the blocker is... | Read... |
| --- | --- |
| confirming which collectors exist on an LTS baseline, comparing defaults, or deciding between G1/ZGC/Shenandoah | `./references/collector-baselines.md` |
| configuring GC logging for next deploy, interpreting GC log output, translating JDK 8 legacy flags to unified logging | `./references/gc-logging.md` |
| analyzing JFR recordings for GC events, querying specific events, interpreting event output | `./references/jfr-gc-events.md` |

## Invariants

- MUST identify the active Java version or LTS baseline before discussing collector availability or defaults.
- MUST distinguish pause-time, throughput, and footprint goals before recommending a collector.
- SHOULD prefer the default collector unless evidence shows a mismatch.
- MUST gather or use runtime evidence before recommending non-trivial GC flag changes.
- MUST explain collector tradeoffs in workload terms, not only by naming algorithms.
- MUST avoid presenting preview, experimental, or withdrawn options as default production advice.
- MUST distinguish JDK 8-era GC logging from JDK 9+ unified logging.
- MUST verify collector availability against the actual JDK vendor or distribution when the collector is not a default HotSpot baseline.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| switching collectors before reading evidence | collector change becomes guesswork | confirm the collector and read logs/JFR first |
| discussing ZGC or Shenandoah without the LTS baseline | availability and maturity assumptions can be wrong | anchor the conversation in the deployed Java LTS first |
| recommending Shenandoah without checking the vendor build | some distributions do not ship it at all | confirm the actual JDK vendor or distribution before suggesting it |
| recommending `-Xlog:gc` for a JDK 8 runtime | unified logging is not available there | use legacy GC logging flags on JDK 8 and earlier |
| recommending CMS on a modern runtime | CMS was removed and old guidance may no longer apply | treat CMS as a legacy JDK 8-era option only |
| tuning flags before proving GC is the bottleneck | the real issue may be allocation rate, leaks, or non-GC runtime behavior | verify that the symptom is genuinely GC-driven |
| reading GC symptoms without stating the workload goal | pause, throughput, and footprint recommendations diverge | name the dominant operational goal explicitly |

## Scope Boundaries

- Activate this skill for:
  - GC evidence gathering and interpretation
  - collector tradeoffs and LTS-boundary differences
  - pause and heap-pressure triage
- Do not use this skill as the primary source for:
  - general JVM incident triage without GC focus
  - Java language, testing, or dependency-management guidance
  - standard JDK packaging and module workflows
