# JVM diagnostic capture templates

Lightweight first triage:

```sh
jcmd -l
jcmd <pid> VM.version
jcmd <pid> VM.flags
jcmd <pid> Thread.print -l
```

Use when: you have only a vague "the JVM is slow" report or one incomplete stack trace.

Single thread dump with lock detail:

```sh
jcmd <pid> Thread.print -l > /path/to/private-diagnostics/thread-dump.txt
```

Use when: the issue looks like blocking, deadlock, starvation, or lock contention and you need the first snapshot.

> [!WARNING]
>
> Thread dumps can contain thread names, stack traces, class names, and other runtime details that may expose request paths or internal system structure.
> Write thread dumps to a restricted diagnostics path rather than a shared working directory, and clean them up after analysis like other sensitive captures.

Low-overhead JFR start for a running JVM:

```sh
jcmd <pid> JFR.start name=baseline settings=default disk=true maxage=6h
jcmd <pid> JFR.check
```

Use when: the issue depends on time-based evidence such as CPU, allocation, I/O, or lock behavior.
Stop the recording when the investigation finishes:

```sh
jcmd <pid> JFR.stop name=baseline
```

Leave a recording you did not start untouched unless the owner authorizes a change.
Add `filename=` to `JFR.stop` when the captured data must survive the stop.

Startup-attached JFR:

```sh
java -XX:StartFlightRecording=name=startup,settings=profile,filename=/path/to/private-diagnostics/startup.jfr,dumponexit=true -jar app.jar
```

Use when: the problem happens during startup, very early request handling, or any phase that might be missed if JFR starts later via `jcmd`.

> [!IMPORTANT]
>
> JFR recordings can contain stack traces, class names, request metadata, and other sensitive runtime details.
> Prefer a private diagnostics directory with restrictive permissions instead of a shared location such as `/tmp`, and clean up captures promptly after analysis.
>
> For JDK 8, treat JFR as a special-case workflow, not the default path.
> Verify that the target runtime and operational policy actually permit Flight Recorder before recommending these commands.

Heap-oriented escalation:

```sh
jcmd <pid> GC.heap_info
jcmd <pid> GC.class_histogram
```

> [!NOTE]
>
> `GC.class_stats` was removed in JDK 15 and should not be treated as a current default diagnostic command.
> Use `GC.class_histogram` for heap class analysis on modern JVMs.

Use when: the symptom is memory growth, allocation pressure, or suspected heap retention.

Native-memory escalation:

```sh
jcmd <pid> VM.native_memory summary
```

Use when: RSS or container memory keeps growing but heap evidence alone does not explain the pressure.

Important setup note:

- Native Memory Tracking must be enabled at JVM startup with `-XX:NativeMemoryTracking=summary` or `-XX:NativeMemoryTracking=detail`

Legacy `jstack` thread dump:

```sh
jstack -l <pid>
```

Use when: you need the traditional `jstack` output shape or are working in an older operational workflow that still documents `jstack`.

Legacy `jmap` histogram and dump:

```sh
jmap -histo <pid>
jmap -dump:live,format=b,file=/path/to/private-diagnostics/heap.hprof <pid>
```

Use when: you specifically need the standalone `jmap` form instead of the newer `jcmd` command family.

> [!WARNING]
>
> Heap dumps are highly sensitive artifacts and can contain credentials, tokens, session state, and PII.
> Write them only to restricted paths, transfer them over approved secure channels, and delete them as soon as the investigation allows.

Postmortem `jhsdb` core analysis:

```sh
jhsdb jstack --exe "$JAVA_HOME/bin/java" --core /path/to/private-diagnostics/core
jhsdb jmap --exe "$JAVA_HOME/bin/java" --core /path/to/private-diagnostics/core --heap
```

Use when: the JVM has already crashed or you need core-file inspection rather than routine live attach.
