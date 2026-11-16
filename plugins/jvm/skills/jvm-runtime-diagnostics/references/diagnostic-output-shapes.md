# Diagnostic output shapes

## `jcmd -l` Output (JVM Discovery)

```text
12345 com.example.App /opt/app/app.jar
```

Read: PID = 12345, main class = `com.example.App`, launch path = `/opt/app/app.jar`.
Use this PID for all subsequent `jcmd <pid>` commands.

If no JVMs appear, either no JVM is running, or the current user/namespace cannot see the target process.

## `Thread.print -l` Thread Dump Shape

Each thread block follows this structure:

```text
"http-nio-8080-exec-42" #284 daemon prio=5 os_prio=0 cpu=120.00ms elapsed=320.50s tid=0x00007f9a2c01e800 nid=0x4e03 waiting on condition [0x00007f99c4bfe000]
   java.lang.Thread.State: TIMED_WAITING (parking)
    at sun.misc.Unsafe.park(Native Method)
    - parking to wait for  <0x00000006f1a9d040> (a java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject)
    at java.util.concurrent.locks.LockSupport.parkNanos(LockSupport.java:257)
    at java.util.concurrent.SynchronousQueue$TransferStack.awaitFulfill(SynchronousQueue.java:453)
    at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1065)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1127)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
    at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:61)
    at java.lang.Thread.run(Thread.java:840)
   Locked ownable synchronizers:
    - None
```

Key fields per thread:

| Field | Location | How to read it |
| --- | --- | --- |
| Thread name | `"http-nio-8080-exec-42"` | Pool threads follow the application thread naming convention |
| Thread state | `java.lang.Thread.State: TIMED_WAITING (parking)` | See state table below |
| CPU time | `cpu=120.00ms` | Total CPU consumed by this thread since start |
| Elapsed time | `elapsed=320.50s` | Wall-clock time since thread started |
| Native ID | `nid=0x4e03` | OS-level thread ID for `top -H` or `strace -p` correlation |
| Stack trace | indented lines below state | Most recent call first, with native frames shown as `(Native Method)` |
| Lock info | `Locked ownable synchronizers:` | `- None` means no `ReentrantLock` held |

Thread states and what they mean:

| State | Normal when... | Concern when... |
| --- | --- | --- |
| `RUNNABLE` | Thread is actively executing CPU work | Many threads RUNNABLE + high CPU = saturation |
| `BLOCKED` | Waiting to enter a `synchronized` block | Same lock contested by many threads = contention |
| `TIMED_WAITING` | Sleeping, parked with timeout (`Thread.sleep`, `parkNanos`) | Stuck in TIMED_WAITING indefinitely = hung operation |
| `WAITING` | Waiting indefinitely (`Object.wait()`, `LockSupport.park`) | Never recovers = deadlock or missed signal |
| `NEW` / `TERMINATED` | Thread not yet started or already finished | Large counts of NEW threads = thread leak |

## `GC.heap_info` Output

```text
Min heap alignment: 4096 KiB
G1 Heap:
   num_regions: 512
   Heap size: 1048576K
   Free regions: 200
   Young regions: 180
   Eden regions: 160
   Survivor regions: 20
   Old regions: 132
```

Read: Total heap size vs free/young/old region distribution.
If `Free regions` drops low during load, heap pressure exists.

## `GC.class_histogram` Output (Top Lines)

```text
num     #instances         #bytes  class name
----------------------------------------------
    1:          48000     15360000  [B
    2:          32000      8320000  [Ljava.lang.Object;
    3:          25000      6000000  com.example.model.Data
    4:           5000      2400000  java.util.concurrent.ConcurrentHashMap$Node
```

Read: rank → instance count → total bytes → class name.
`[B` = byte arrays, `[L...;` = object arrays.
Focus on top 5–10 classes by bytes.
If a single application class dominates, investigate retention in that class.

## `VM.native_memory summary` Output

```text
Native Memory Tracking:

Total: reserved=1024MB, committed=512MB

- Java Heap (reserved=512MB, committed=256MB)
- Class (committed=64MB)
- Thread (committed=48MB # of 24 threads)
- Code (committed=32MB)
- GC (committed=96MB)
- Internal (committed=16MB)
- Symbol (committed=12MB)
- Native Memory Tracking (committed=8MB)
- Compiler (committed=24MB)
- Internationalization (committed=4MB)
```

Read: If total committed approaches container limit but Java Heap is small, non-heap categories (Code, GC, Thread, Compiler) may be the real pressure source.
`# of N threads` shows live thread count.

## `JFR.check` Output

```text
Recording 1: name=baseline maxage=6 h (running)
```

Read: Match the recording `name`, confirm `(running)` before claiming the capture is active, and verify `maxage` matches the intended retention window.
If the runtime also reports a destination or path, confirm it points to the restricted diagnostics location you intended.
