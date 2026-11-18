---
metadata:
  reference:
    jcmd:
      version: JDK 25
      url: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html
    JFR presets:
      version: JDK 25
      url:
        - https://raw.githubusercontent.com/openjdk/jdk/jdk-25%2B36/src/jdk.jfr/share/conf/jfr/default.jfc
        - https://raw.githubusercontent.com/openjdk/jdk/jdk-25%2B36/src/jdk.jfr/share/conf/jfr/profile.jfc
    OpenJDK JDK 27 release:
      version: JDK 27
      url: https://openjdk.org/projects/jdk/27/
    JEP 523:
      version: JDK 27
      url: https://openjdk.org/jeps/523
name: jvm-gc-diagnostics
description: >-
  Interpret GC logs and JFR evidence, diagnose heap pressure, or compare JVM garbage collectors.
---

# JVM GC Diagnostics

## Goal

Analyze garbage-collection symptoms, collector choices, and runtime evidence using standard JDK and HotSpot tooling.
The common case is not switching collectors immediately.
It is proving whether GC is actually the bottleneck, then comparing realistic collector options against the deployed Java baseline.
Focus on pauses, throughput, allocation pressure, heap behavior, and what is actually available on the running JDK before touching tuning folklore.

Treat JDK 8, 11, 17, 21, and 25 as the supported LTS reference line for this skill, and anchor collector guidance to the actual deployed runtime instead of assuming the newest LTS behavior.

Treat JFR-based GC evidence as the normal low-overhead path on JDK 11 and later.
On JDK 8, verify the exact Oracle JDK 8 Flight Recorder availability, commercial-feature status, and licensing posture before recommending JFR commands.
Otherwise prefer GC logs plus `jcmd` evidence first.

## Task Context

Confirm the deployed runtime, active collector, and pause, throughput, or footprint goal from available evidence.
Collect additional evidence only when it can resolve a material uncertainty.
Use [collector baselines](./references/collector-baselines.md) for availability and tradeoffs on the deployed JDK.
Use [GC logging](./references/gc-logging.md) for log interpretation or configuration.
Use [JFR GC events](./references/jfr-gc-events.md) for recording selection and event analysis.

## Diagnostic Authority

Confirm the target and authorized capture scope before attaching to a live JVM.
Check command impact before histograms, heap dumps, recording changes, or launch-flag changes.
Ask before actions outside that scope, not before each already-authorized capture.
Use restricted diagnostic destinations and preserve existing artifacts.
Stop recordings started for the investigation when finished; leave others untouched unless their owner authorizes a change.
Retain GC captures only as long as the investigation needs.

## Minimal Setup

For a running JVM, confirm the process and current runtime first:

```sh
jcmd -l
jcmd <pid> VM.version
jcmd <pid> VM.flags
```

For the next deploy, enable bounded GC log output for basic pause visibility:

```sh
java -Xlog:gc:file=/path/to/private-diagnostics/gc-%p-%t.log:uptimemillis,pid:filecount=5,filesize=10M ...
```

Version-specific logging syntax - JDK 8 and earlier use legacy GC log flags (`-verbose:gc`, `-XX:+PrintGCDetails`, `-XX:+PrintGCTimeStamps`, `-Xloggc:/path/to/private-diagnostics/gc.log`).
JDK 9 and later use unified logging with `-Xlog:gc...`.

## First Runnable Commands or Code Shape

Start with the smallest GC evidence set:

```sh
jcmd -l
jcmd <pid> VM.flags
jcmd <pid> VM.command_line
jcmd <pid> GC.heap_info
jcmd <pid> JFR.start name=gc-baseline settings=default disk=true maxage=2h
```

Use when: you have a live JVM and need first-line GC evidence before discussing collectors.

Use [GC capture templates](./references/gc-capture-templates.md) when gathering heap, recording, logging, or collector-comparison evidence.

## Completion

Identify the running JDK and collector from the capture and interpret flags against that version's logging and collector support.
Tie the recommendation to the observed pause, throughput, or footprint requirement.
Recommend a collector change only when the evidence shows the current collector misses that requirement.
Name the measurement or authorized rollout decision still needed.

Use [output shapes](./references/diagnostic-output-shapes.md) when interpreting GC heap, collector flags, JFR status, or GC log output.

## Invariants

- MUST identify the active Java version or LTS baseline before discussing collector availability or defaults.
- MUST distinguish pause-time, throughput, and footprint goals before recommending a collector.
- SHOULD prefer the default collector unless evidence shows a mismatch.
- MUST gather or use runtime evidence before recommending non-trivial GC flag changes.
- MUST explain collector tradeoffs in workload terms instead of naming algorithms alone.
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
| tuning flags before proving GC is the bottleneck | the real issue may be allocation rate, leaks, or non-GC runtime behavior | verify that the symptom is GC-driven |
| reading GC symptoms without stating the workload goal | pause, throughput, and footprint recommendations diverge | name the dominant operational goal explicitly |

## Scope Boundaries

- Activate this skill for:
  - GC evidence gathering and interpretation
  - collector tradeoffs and LTS-boundary differences
  - pause and heap-pressure triage
- Do not use this skill as the primary source for: general JVM incident triage without GC focus
  - Java language, testing, or dependency-management guidance
  - standard JDK packaging and module workflows
