---
metadata:
  reference:
    Netty:
      version: 4.2.18.Final
      url:
        - https://github.com/netty/netty/releases/tag/netty-4.2.18.Final
        - https://raw.githubusercontent.com/netty/netty/netty-4.2.18.Final/transport-native-io_uring/README.md
    Netty BOM:
      url: https://repo.maven.apache.org/maven2/io/netty/netty-bom/maven-metadata.xml
    Netty native transports:
      url: https://netty.io/wiki/native-transports.html
    Netty API:
      version: 4.2
      url:
        - https://netty.io/4.2/api/io/netty/channel/uring/IoUringServerSocketChannel.html
        - https://netty.io/4.2/api/io/netty/channel/epoll/EpollEventLoopGroup.html
        - https://netty.io/4.2/api/io/netty/channel/MultiThreadIoEventLoopGroup.html
    Netty maintenance:
      version: 4.1.138.Final
      url: https://github.com/netty/netty/releases/tag/netty-4.1.138.Final
    Netty development branch:
      version: "5.0"
      url: https://github.com/netty/netty/tree/5.0
name: netty
description: >-
  Build or debug Netty bootstraps, channel pipelines, ByteBuf ownership, framing, and codecs.
---

# Netty

## Official Baseline

- These examples target the official Netty 4.2.x.Final line.
  The 4.2.18.Final release is this guide's reviewed baseline, not a dependency pin.
- For existing Netty 4.1.x.Final deployments, check the project's managed version and Netty's current support status and consult the Netty 4.2 migration guide before upgrading.
  The 4.1.138.Final release is this guide's reviewed maintenance baseline.
- Before adding or upgrading Netty, check `io.netty:netty-bom` in Maven Central for the latest stable release compatible with the project's Java version and Netty line.
  Honor an existing platform, BOM, or project pin, and keep Netty modules and native transports aligned with it.
  Set `${netty.version}` to the selected release only when the build does not manage it.
- Treat Netty 5 as a development line until the official Netty project marks it stable.

Preserve transport, pipeline, lifecycle, and buffer ownership contracts.

## Operating rules

- Keep the common path on core Netty APIs: `Bootstrap`, `ServerBootstrap`, `Channel`, `ChannelPipeline`, handlers, `ByteBuf`, and codec basics.
- Treat `ByteBuf` and other `ReferenceCounted` messages as owned resources.
  - Release them exactly once unless ownership moves downstream.
- Do not block an `EventLoop` thread with long-running work.
  - Offload CPU-heavy or blocking work before the pipeline stalls.
- Use one handler per pipeline unless it is stateless and safe for `@Sharable`.
- Shut down every `EventLoopGroup` with `shutdownGracefully()`.

## When this skill fits

Use this skill for direct Netty TCP or UDP clients and servers, bootstrap setup, channel lifecycle, inbound and outbound pipeline flow, `ByteBuf` operations and ownership, framing, and codecs.

For builder-driven reactive transport centered on `HttpServer`, `HttpClient`, `TcpServer`, `TcpClient`, `UdpServer`, or `UdpClient`, use the higher-level reactive transport surface instead of low-level pipeline APIs.

## Task Context

Read the bootstrap, pipeline, and tests for transport, message ownership, and lifecycle owner.
Keep existing resource wiring unless the task requires a change.
Use the [references](#references) for the changed topic.
Examples do not authorize public listeners, external connections, or changes to deployed resources.

## Core model

### Event loop and threading

- A `Channel` stays on one `EventLoop` for its lifetime; inbound and outbound callbacks run there unless offloaded.
- The boss group accepts connections; the worker group handles accepted channel I/O.

### Channel and future lifecycle

- `bind(...)`, `connect(...)`, `writeAndFlush(...)`, and `close(...)` are asynchronous and return `ChannelFuture`.
- At process boundaries, `sync()` may simplify startup or shutdown.
- Never wait on an unfinished `ChannelFuture` on an I/O thread.
- Use listeners instead of blocking when you need non-blocking continuation.

### Bootstrap options

- `ServerBootstrap.option(...)` configures the listening server socket that accepts connections.
- `ServerBootstrap.childOption(...)` configures each accepted child channel handled by the worker group.
- `Bootstrap.option(...)` configures the single client or UDP channel because there is no child-channel split.

### Pipeline direction

- Inbound events move from head to tail through decoders and inbound business handlers.
- Outbound events move from tail to head through encoders and outbound handlers.

### Buffer and codec basics

- `ByteBuf` has separate reader and writer indexes and does not require `flip()`.
- Use `ctx.alloc()` or `channel.alloc()` to allocate buffers that match the channel allocator.
- A codec usually does one transformation step.
  - `ByteToMessageDecoder`: bytes to higher-level messages
  - `MessageToByteEncoder<T>`: messages to bytes

## Transport examples

For dependencies or TCP client, TCP server, or UDP server setup, read [dependency and transport examples](references/transport-examples.md).
Use `netty-all` for development convenience or individual modules for production.
For a UDP reply, `writeAndFlush` owns the new reference from `retainedDuplicate`; `SimpleChannelInboundHandler` releases the original packet.

## Pipeline examples

For echo handlers, framing and POJO codec chains, manual buffer release, or write-then-close, read [pipeline examples](references/pipeline-examples.md).
Separate byte-level concerns from business logic and place decoders, business handlers, and encoders according to pipeline direction.
Use `ChannelFutureListener.CLOSE` when the channel must close after a write completes.

## Decision points

Choose TCP for ordered reliable streams and UDP for datagram-oriented protocols when loss or reordering is acceptable.
Use `ServerBootstrap` for listening TCP sockets and `Bootstrap` for clients and UDP.
Use NIO first, automatic reads for ordinary servers, and a simple handler for existing frames.
Use `SimpleChannelInboundHandler` for one inbound type; decide ownership explicitly for shared or asynchronous buffers.
For TCP versus UDP startup, event loops, channels, and framing, read [transport selection](references/transport-selection.md).

## Completion

Verify the changed contract with the existing native tests.
Use `EmbeddedChannel` for handler or codec behavior that does not require a real transport.
Check only the relevant invariants below, including resource cleanup on changed error and cancellation paths.

Check channel class, server socket versus child options, boss/worker groups, pipeline order, exact buffer release, and graceful group shutdown.
If `AUTO_READ` is `false`, call `channel.read()` in `channelActive()` and after each processed message.

Separate framing, decoding, encoding, and business handlers by responsibility.

## References

Open these advanced references if needed:

| Blocker | Open |
| --- | --- |
| retain/release, leak detection, async ownership | [reference-counting.md](./references/reference-counting.md) |
| stateful decoders, `LengthFieldBasedFrameDecoder`, sharability rules | [custom-codec.md](./references/custom-codec.md) |
| line-, delimiter-, fixed-size-, or length-based framing choices | [framing.md](./references/framing.md) |
| epoll, kqueue, or io_uring selection and fallback | [native-transport.md](./references/native-transport.md) |
| `AUTO_READ`, manual `channel.read()`, flow-controlled consumption | [backpressure.md](./references/backpressure.md) |
| testing handlers and pipelines with `EmbeddedChannel` | [testing-embedded-channel.md](./references/testing-embedded-channel.md) |
| TLS or SSL pipeline setup | [tls-ssl.md](./references/tls-ssl.md) |
| heartbeat, timeout, or stale connection handling | [idle-handling.md](./references/idle-handling.md) |

## Result

Report transport, pipeline, ownership, or shutdown consequences, checks, and unverified network or lifecycle behavior.
