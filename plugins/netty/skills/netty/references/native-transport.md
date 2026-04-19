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

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-transport-native-epoll</artifactId>
    <version>${netty.version}</version>
    <classifier>linux-x86_64</classifier>
</dependency>
```

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
