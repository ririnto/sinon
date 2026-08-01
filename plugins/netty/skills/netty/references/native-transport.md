---
description: >-
  Open this when a Netty deployment needs epoll, kqueue, or io_uring instead of the default NIO transport.
---

# Native Transport Selection

## Open this when

Open this when Linux or macOS deployment needs native transport features, lower latency, higher connection counts, or platform-specific socket options.

## Transport choice

| Transport | Platform | Default use |
| --- | --- | --- |
| NIO | all platforms | start here |
| epoll | Linux | high-throughput production servers |
| kqueue | macOS and BSD | native readiness on those systems |
| io_uring | Linux | first-class transport in Netty 4.2; opt-in when kernel 5.14+ and the native dependency are available |

## Dependency pattern

Linux epoll on x86_64:

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-transport-native-epoll</artifactId>
    <version>${netty.version}</version>
    <classifier>linux-x86_64</classifier>
</dependency>
```

Linux epoll on aarch64 or ARM64:

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-transport-native-epoll</artifactId>
    <version>${netty.version}</version>
    <classifier>linux-aarch_64</classifier>
</dependency>
```

macOS or BSD kqueue:

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-transport-native-kqueue</artifactId>
    <version>${netty.version}</version>
    <classifier>osx-x86_64</classifier>
</dependency>
```

Linux io_uring on Java 9+ and kernel 5.14+ (a first-class transport since Netty 4.2, replacing the `netty-incubator-transport-io_uring` module).
The `netty-transport-native-io_uring` README lists kernel 5.14 with `CONFIG_IO_URING=y` as the floor:

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-transport-native-io_uring</artifactId>
    <version>${netty.version}</version>
    <classifier>linux-x86_64</classifier>
</dependency>
```

Choose the classifier that matches the target OS and architecture.
The classifier determines which native library (`.so`, `.dylib`, `.dll`) is bundled in the artifact.

## Conditional fallback for NIO, epoll, and kqueue

The sample intentionally covers NIO, epoll, and kqueue.
Add an explicit io_uring branch when opting into it.

```java
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;

EventLoopGroup bossGroup;
EventLoopGroup workerGroup;
Class<? extends ServerChannel> serverChannelClass;

if (Epoll.isAvailable()) {
    bossGroup = new MultiThreadIoEventLoopGroup(1, EpollIoHandler.newFactory());
    workerGroup = new MultiThreadIoEventLoopGroup(EpollIoHandler.newFactory());
    serverChannelClass = EpollServerSocketChannel.class;
} else if (KQueue.isAvailable()) {
    bossGroup = new MultiThreadIoEventLoopGroup(1, KQueueIoHandler.newFactory());
    workerGroup = new MultiThreadIoEventLoopGroup(KQueueIoHandler.newFactory());
    serverChannelClass = KQueueServerSocketChannel.class;
} else {
    bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    serverChannelClass = NioServerSocketChannel.class;
}
```

The `MultiThreadIoEventLoopGroup` + `IoHandler` form requires Netty 4.2 or later.
In Netty 4.2, the transport-specific event loop groups such as `EpollEventLoopGroup` and `KQueueEventLoopGroup` are deprecated in favor of the `IoHandlerFactory` constructor.

## Channel mapping for this fallback

| Transport | Server | Client | UDP |
| --- | --- | --- | --- |
| NIO | `NioServerSocketChannel` | `NioSocketChannel` | `NioDatagramChannel` |
| epoll | `EpollServerSocketChannel` | `EpollSocketChannel` | `EpollDatagramChannel` |
| kqueue | `KQueueServerSocketChannel` | `KQueueSocketChannel` | `KQueueDatagramChannel` |

## Guidance

- Start with NIO unless the deployment environment is known and controlled.
- Keep fallback logic explicit so development machines still run.
- Treat io_uring as an explicit opt-in, not the common path.
- In Netty 4.2, io_uring graduated from incubator to a supported transport.
  - The classes use the `IoUring*` prefix in `io.netty.channel.uring`, for example `IoUringServerSocketChannel` and `IoUringIoHandler.newFactory()`.
  - The old `IOUring*` incubator class names no longer exist.
