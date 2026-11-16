# GC capture and collector-comparison templates

Live JVM GC first pass:

```sh
jcmd <pid> VM.version
jcmd <pid> VM.command_line
jcmd <pid> VM.flags
jcmd <pid> VM.flags -all
jcmd <pid> GC.heap_info
jcmd <pid> GC.class_histogram
```

Use when: the symptom is heap pressure, memory growth, or unexplained GC churn.

Low-overhead JFR GC recording:

```sh
jcmd <pid> JFR.start name=gc-baseline settings=default disk=true maxage=2h
jcmd <pid> JFR.check
jcmd <pid> JFR.dump name=gc-baseline filename=/path/to/private-diagnostics/gc-baseline-%p-%t.jfr
```

Use when: the pause or latency problem depends on runtime behavior over time.
Stop the recording after the dump when the investigation finishes:

```sh
jcmd <pid> JFR.stop name=gc-baseline
```

Leave a recording you did not start untouched unless the owner authorizes a change.

Startup-attached GC-oriented JFR:

```sh
java -XX:StartFlightRecording=name=gc-startup,settings=profile,filename=/path/to/private-diagnostics/gc-startup.jfr,dumponexit=true -Xlog:gc=debug:file=/path/to/private-diagnostics/gc-%p-%t.log:uptimemillis,pid:filecount=5,filesize=10M -jar app.jar
```

Use when: you need GC and allocation evidence from process start instead of after a later live attach.

> [!IMPORTANT]
>
> GC-oriented JFR captures and GC log files can still expose sensitive runtime details.
> Prefer a private diagnostics directory with restrictive permissions, and keep retention only as long as the investigation needs.
>
> On JDK 8, do not present JFR as the routine default.
> Confirm Oracle JDK 8 Flight Recorder availability and policy first, or stay with GC logs plus `jcmd` evidence when that is uncertain.

Bounded GC logging for the next deploy:

```sh
java -Xlog:gc=debug:file=/path/to/private-diagnostics/gc-%p-%t.log:uptimemillis,pid:filecount=5,filesize=10M ...
```

Use when: you cannot diagnose the current issue from existing captures and need better next-run evidence.

Legacy JDK 8 GC logging:

```sh
java -verbose:gc -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:/path/to/private-diagnostics/gc.log ...
```

Use when: the deployed runtime is JDK 8 or earlier and `-Xlog` is not available.

Startup-time collector verification:

```sh
java -XX:+PrintCommandLineFlags -version
```

Use when: you are validating a launch configuration or checking whether a proposed collector flag is actually accepted.

Unified logging inspection:

```sh
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
