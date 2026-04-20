---
title: Performance Review Patterns
description: >-
  Reference for concrete JFR profiling commands, allocation diagnosis,
  lock contention investigation, and evidence interpretation heuristics
  in Java performance review work.
---

Open this reference when the bottleneck type is already classified and you still need one of these deeper jobs:

- attach JFR to a live JVM or read an existing recording
- diagnose allocation-heavy paths with class histograms
- investigate lock contention from thread dumps
- establish a throughput vs latency baseline
- interpret profiling evidence with disciplined review prompts

## Concrete profiling and evidence commands

Attach JFR to a live JVM when the process is already running:

```bash
jcmd <pid> JFR.start name=profile settings=profile disk=true maxage=10m
jcmd <pid> JFR.dump name=profile filename=/tmp/profile.jfr
```

Reading a JFR recording for allocation and lock evidence:

```bash
# List available JFR events and filters:
jcmd <pid> JFR.check

# Dump for later analysis:
jcmd <pid> JFR.dump name=profile filename=/tmp/profile.jfr
```

Allocation-heavy path diagnosis (look for `java.lang.String` or byte-buffers in the hot path):

```bash
# Class histogram to find top allocators:
jcmd <pid> GC.class_histogram | head -50

# Live histogram (stops the world briefly — use with care in prod):
jcmd <pid> GC.class_histogram -all
```

Lock contention diagnosis:

```bash
# Thread dump with lock detail:
jcmd <pid> Thread.print -l

# Run three times 5 seconds apart, compare for blocked threads:
for i in 1 2 3; do jcmd <pid> Thread.print -l > thread-$i.txt; sleep 5; done
```

Throughput vs latency baseline:

```bash
# Verify selected JVM flags at startup:
java -XX:+PrintCommandLineFlags -version

# Capture a short JFR recording to get allocation rate and thread states:
jcmd <pid> JFR.start name=baseline settings=default disk=true maxage=2h
# let workload run...
jcmd <pid> JFR.dump name=baseline filename=/tmp/baseline.jfr
```

## Review prompts for evidence interpretation

- Separate startup costs from steady-state costs.
- Compare contention, allocation churn, serialization, and parsing before proposing a fix.
- Match the recommendation to the measured workload rather than a generic optimization rule.
