---
title: Native Transport Selection
description: Open this when a Netty deployment needs epoll, kqueue, or io_uring instead of the default NIO transport.
---

## Open this when

Open this when Linux or macOS deployment needs native transport features, lower latency, higher connection counts, or platform-specific socket options.

## Transport choice

| Transport | Platform | Default use |
| --- | --- | --- |
| NIO | all platforms | start here |
| epoll | Linux | high-throughput production servers |
| kqueue | macOS and BSD | native readiness on those systems |
| io_uring | newer Linux | only when the environment and dependency are explicitly supported |

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

Linux io_uring on JDK 21+ and kernel 5.11+:

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-transport-native-io_uring</artifactId>
    <version>${netty.version}</version>
    <classifier>linux-x86_64</classifier>
</dependency>
```

Choose the classifier that matches the target OS and architecture. The classifier determines which native library (`.so`, `.dylib`, `.dll`) is bundled in the artifact.

## Conditional fallback

```java
EventLoopGroup bossGroup;
EventLoopGroup workerGroup;
Class<? extends ServerChannel> serverChannelClass;

if (Epoll.isAvailable()) {
    bossGroup = new EpollEventLoopGroup(1);
    workerGroup = new EpollEventLoopGroup();
    serverChannelClass = EpollServerSocketChannel.class;
} else if (KQueue.isAvailable()) {
    bossGroup = new KQueueEventLoopGroup(1);
    workerGroup = new KQueueEventLoopGroup();
    serverChannelClass = KQueueServerSocketChannel.class;
} else {
    bossGroup = new NioEventLoopGroup(1);
    workerGroup = new NioEventLoopGroup();
    serverChannelClass = NioServerSocketChannel.class;
}
```

## Channel mapping

| Transport | Server | Client | UDP |
| --- | --- | --- | --- |
| NIO | `NioServerSocketChannel` | `NioSocketChannel` | `NioDatagramChannel` |
| epoll | `EpollServerSocketChannel` | `EpollSocketChannel` | `EpollDatagramChannel` |
| kqueue | `KQueueServerSocketChannel` | `KQueueSocketChannel` | `KQueueDatagramChannel` |

## Guidance

- Start with NIO unless the deployment environment is known and controlled.
- Keep fallback logic explicit so development machines still run.
- Treat io_uring as an explicit opt-in, not the common path.
