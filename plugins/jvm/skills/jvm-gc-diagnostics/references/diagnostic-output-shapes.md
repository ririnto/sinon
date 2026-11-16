# Diagnostic output shapes

## `GC.heap_info` Output

```sh
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

Read: Total heap = 1048576K (~1GB), 200 free regions (39% free).
Young gen = 180 regions (35%), Old gen = 132 regions (26%).
If `Free regions` drops below ~50 during sustained load, the heap is under pressure.

## `VM.flags` Output (Collector Identity)

```sh
jcmd <pid> VM.flags
```

Look for these flag lines to identify the active collector:

```sh
-XX:G1HeapRegionSize=4M
-XX:+UseG1GC
-XX:+UseZGC
-XX:+UseParallelGC
-XX:+UseSerialGC
```

Read: `G1HeapRegionSize` or `UseG1GC` indicates G1, `UseZGC` indicates ZGC, `UseParallelGC` indicates Parallel GC, and `UseSerialGC` indicates Serial GC.

If none of these appear, inspect `jcmd <pid> VM.flags -all` for the active collector flags.
On JDK 9-26, constrained environments can select Serial instead of the server-class G1 default.
JDK 27 selects G1 by default in all environments.

## `JFR.check` Output

```sh
jcmd <pid> JFR.check
```

Supported output cues to confirm:

```text
Recording 1: name=gc-baseline maxage=2 h (running)
```

Read: Match the recording `name`, confirm `(running)` before claiming the capture is active, and verify `maxage` matches the intended retention window.
If the runtime also reports a destination or path, confirm it points to the restricted diagnostics location you intended.

## Unified GC Log Line Shape (JDK 9+)

```text
[0.952s][info][gc,start ] GC(0) Pause Young (Normal) (G1 Evacuation Pause)
[0.956s][info][gc,cpu  ] GC(0) User=0.01s Sys=0.00s Real=0.00s
```

Read each event: timestamp → log level → tag(s) → GC ID + phase → details.
The `Real=` value in the `[gc,cpu]` line is the wall-clock pause duration.

## Legacy GC Log Line Shape (JDK 8)

```text
2026-04-20T10:15:30.123+0000: 1.234: [GC (Allocation Failure) ...] 196608K->139264K(524288K), 0.0234321 secs
```

Read: datestamp → uptime → GC type → total heap Before->After(Max) → pause seconds.
The value before `secs` is the wall-clock pause duration.

## `jfr summary` Output

```sh
jfr summary /path/to/private-diagnostics/recording.jfr
```

Shows event counts grouped by type.
Look for:

- `jdk.GarbageCollection` count - how many GC cycles occurred
- `jdk.GCPhasePause` - individual pause phases.
  - Check max duration
- `jdk.ObjectAllocationInNewTLAB` count - very high counts indicate allocation pressure
