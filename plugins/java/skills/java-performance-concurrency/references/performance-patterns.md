---
description: >-
  Reference for concrete JFR profiling commands, allocation diagnosis, lock contention investigation, and evidence interpretation heuristics in Java performance review work.
---

# Performance Review Patterns

Open this reference when the bottleneck type is already classified and you still need one of these deeper jobs:

- attach JFR to a live JVM or read an existing recording
- diagnose allocation-heavy paths with class histograms
- investigate lock contention from thread dumps
- establish a throughput vs latency baseline
- interpret profiling evidence with disciplined review prompts

## Concrete profiling and evidence commands

Attach JFR to a live JVM when the process is already running:

```sh
jcmd <pid> JFR.start name=profile settings=profile disk=true maxage=10m
jcmd <pid> JFR.dump name=profile filename=/tmp/profile-snapshot.jfr
jcmd <pid> JFR.stop name=profile filename=/tmp/profile.jfr
```

Inspect active recordings and their event settings, then save a snapshot without stopping the recording:

```sh
jcmd <pid> JFR.check verbose=true
jcmd <pid> JFR.dump name=profile filename=/tmp/profile-snapshot.jfr
```

`maxage` limits how long recording data is retained on disk, not how long recording continues.
`JFR.dump` leaves the recording active.
`JFR.stop` ends it, and must include `filename` to save the final data.

Allocation-heavy path diagnosis (look for `java.lang.String` or byte-buffers in the hot path):

Class histogram to find top allocators:

```sh
jcmd <pid> GC.class_histogram | head -50
```

Live histogram.
`GC.class_histogram` is a high-impact heap inspection whose cost depends on heap size and content.
Do not assume it causes only a brief pause, especially in production.

```sh
jcmd <pid> GC.class_histogram -all
```

Lock contention diagnosis:

Thread dump with lock detail:

```sh
jcmd <pid> Thread.print -l
```

Compare snapshots from the incident window when one dump cannot distinguish transient waiting from persistent contention.
Collect additional snapshots only within the authorized capture scope.

Throughput vs latency baseline:

Verify selected JVM flags at startup:

```sh
java -XX:+PrintCommandLineFlags -version
```

Capture a JFR recording over the workload window to get allocation rate and thread states, then dump and stop it:

```sh
jcmd <pid> JFR.start name=baseline settings=default disk=true maxage=2h
jcmd <pid> JFR.dump name=baseline filename=/tmp/baseline-snapshot.jfr
jcmd <pid> JFR.stop name=baseline filename=/tmp/baseline.jfr
```

## Review prompts for evidence interpretation

- Separate startup costs from steady-state costs.
- Compare contention, allocation churn, serialization, and parsing before proposing a fix.
- Match the recommendation to the measured workload rather than a generic optimization rule.
