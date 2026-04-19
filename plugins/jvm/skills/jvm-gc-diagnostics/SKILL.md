---
name: jvm-gc-diagnostics
description: >-
  Use this skill when the user asks to "analyze GC logs", "compare G1 and ZGC", "understand Java garbage collection", "debug pause times", "explain heap pressure", or needs guidance on JDK garbage-collection diagnostics and collector tradeoffs.
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

## Use This Skill When

- You need to read GC logs or GC-related JFR evidence.
- You need to compare collectors such as G1, ZGC, Shenandoah, Parallel, or Serial.
- You need to decide whether long pauses or heap pressure are really GC-driven.
- Do not use this skill when the problem is general JVM incident triage without specific GC evidence.

## Common-Case Workflow

1. Start from the symptom: pauses, throughput loss, allocation spikes, memory growth, or startup footprint.
2. Confirm the active collector and the deployed Java version or LTS baseline before discussing alternatives.
3. Gather or read GC evidence first with `jcmd`, GC logs, or JFR.
4. Compare collectors only after the evidence shows GC is the real bottleneck and the default or current collector is a mismatch.
5. Check vendor and build reality before recommending Shenandoah or other non-default collectors.

## Minimal Setup

For a running JVM, confirm the process and current runtime first:

```bash
jcmd
jcmd <pid> VM.version
jcmd <pid> VM.flags
```

For the next deploy, enable bounded GC log output so pauses and allocation pressure are visible:

```bash
java -Xlog:gc:file=gc-%p-%t.log:uptimemillis,pid:filecount=5,filesize=10M ...
```

Legacy note:

- JDK 8 and earlier use legacy GC log flags such as `-verbose:gc`, `-XX:+PrintGCDetails`, `-XX:+PrintGCTimeStamps`, and `-Xloggc:gc.log`
- JDK 9 and later use unified logging with `-Xlog:gc...`

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

## Collector Baselines

| Java baseline | Common/default baseline | Realistic selectable collectors | Notes |
| --- | --- | --- | --- |
| 8 | Parallel on older server-class JVMs | Serial, Parallel, CMS, G1 | pre-unified-logging era; CMS still exists |
| 11 | G1 default | Serial, Parallel, G1, experimental ZGC; Shenandoah depends on build | ZGC needs experimental unlock |
| 17 | G1 default | Serial, Parallel, G1, ZGC; Shenandoah on builds that ship it | ZGC no longer needs experimental unlock |
| 21 | G1 default | Serial, Parallel, G1, ZGC with optional generational mode; Shenandoah on builds that ship it | Treat `-XX:+UseZGC -XX:+ZGenerational` as the explicit generational-ZGC shape on this LTS line |
| 25 | G1 default | Serial, Parallel, G1, generational ZGC; Shenandoah still vendor-sensitive | Use `-XX:+UseZGC`; do not rely on `-XX:+ZGenerational` on this LTS line |

## Version-Specific Collector Notes

- JDK 21 LTS can run ZGC in its explicit generational form with `-XX:+UseZGC -XX:+ZGenerational` when that mode is the intended choice.
- By JDK 25 LTS, the generational-only ZGC path is the practical baseline, so new scripts should use `-XX:+UseZGC` without `-XX:+ZGenerational`.
- Shenandoah remains build- and vendor-sensitive in practice; confirm the actual JDK distribution before recommending it.
- If deployment is pinned to an older LTS in container images, treat that pinned runtime as the effective collector baseline rather than the newest LTS available elsewhere.

## Review Questions

- What Java LTS baseline is actually running in production?
- Is the current collector the default for that baseline?
- Are the proposed collector flags valid for that runtime era?
- Is the problem better explained by allocation rate, heap sizing, or non-GC runtime behavior?
- Is there evidence strong enough to justify moving away from the default collector?

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
