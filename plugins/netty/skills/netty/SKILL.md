---
name: netty
description: Build Netty TCP or UDP clients and servers with Bootstrap, ServerBootstrap, ChannelPipeline, handlers, ByteBuf, and codec basics. Use when creating or debugging core Netty application flow, channel lifecycle, threading, or buffer ownership.
metadata:
  title: Netty
  official_project_url: "https://netty.io/"
  reference_doc_urls:
    - "https://netty.io/wiki/user-guide-for-4.x.html"
    - "https://netty.io/wiki/netty-4.2-migration-guide.html"
    - "https://netty.io/4.2/api/"
    - "https://netty.io/4.1/api/"
  version: "4.x"
  recommended_version: "4.2.x.Final (current stable); 4.1.x.Final remains supported for existing deployments"
---

# Netty

Build one Netty 4.x application path end to end: choose transport, configure bootstrap, assemble the pipeline, handle lifecycle events, and keep buffer ownership correct.

## Operating rules

- Keep the common path on core Netty APIs: `Bootstrap`, `ServerBootstrap`, `Channel`, `ChannelPipeline`, handlers, `ByteBuf`, and codec basics.
- Treat `ByteBuf` and other `ReferenceCounted` messages as owned resources. Release them exactly once unless ownership moves downstream.
- Do not block an `EventLoop` thread with long-running work. Offload CPU-heavy or blocking work before the pipeline stalls.
- Use one handler instance per pipeline unless the handler is truly stateless and safe to mark `@Sharable`.
- Shut down every `EventLoopGroup` with `shutdownGracefully()`.

## When this skill fits

Use this skill for:

- TCP or UDP servers and clients built directly on Netty
- `ServerBootstrap` or `Bootstrap` setup
- `Channel`, `ChannelFuture`, `ChannelPipeline`, inbound and outbound handler flow
- `ByteBuf` allocation, reading, writing, and ownership
- codec structure, framing entrypoints, and handler ordering

Do not use this skill as the common path for builder-driven reactive transport work. If the task is centered on `HttpServer`, `HttpClient`, `TcpServer`, `TcpClient`, `UdpServer`, or `UdpClient`, keep the answer on that higher-level reactive transport surface instead of low-level pipeline APIs.

## Common-path workflow

1. Choose transport and channel class.
   - TCP server: `NioServerSocketChannel`
   - TCP client: `NioSocketChannel`
   - UDP: `NioDatagramChannel`
2. Create the required `EventLoopGroup` instances.
   - Server: boss group accepts connections, worker group handles accepted channels
   - Client or UDP: one group is usually enough
3. Configure `ServerBootstrap` or `Bootstrap` with `group(...)`, `channel(...)`, options, and a `ChannelInitializer`.
4. Build the `ChannelPipeline` in the order bytes should be transformed.
   - framing / decoder
   - inbound business handler
   - encoder / outbound handler
   - keep server socket options on `option(...)` and accepted child-channel options on `childOption(...)`
5. Bind or connect and wait on the resulting `ChannelFuture`.
6. Keep lifecycle handling explicit.
   - `channelActive` / `channelInactive`
   - `exceptionCaught`
   - close future for application shutdown
7. Shut down all event loops gracefully.

## Core model

### Event loop and threading

- A `Channel` is assigned to one `EventLoop` for its lifetime.
- Inbound and outbound callbacks for that channel run on that `EventLoop` thread unless you offload work yourself.
- Server accept happens on the boss group; accepted channel I/O happens on the worker group.

### Channel and future lifecycle

- `bind(...)`, `connect(...)`, `writeAndFlush(...)`, and `close(...)` are asynchronous and return `ChannelFuture`.
- Use `sync()` at process boundaries when you want straightforward startup and shutdown flow.
- Use listeners instead of blocking when you need non-blocking continuation.

### Bootstrap options

- `ServerBootstrap.option(...)` configures the listening server socket that accepts connections.
- `ServerBootstrap.childOption(...)` configures each accepted child channel handled by the worker group.
- `Bootstrap.option(...)` configures the single client or UDP channel because there is no child-channel split.

```java
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(bossGroup, workerGroup)
    .channel(NioServerSocketChannel.class)
    .option(ChannelOption.SO_BACKLOG, 256)
    .childOption(ChannelOption.SO_KEEPALIVE, true)
    .childOption(ChannelOption.TCP_NODELAY, true)
    .childHandler(new EchoInitializer());

Bootstrap clientBootstrap = new Bootstrap();
clientBootstrap.group(group)
    .channel(NioSocketChannel.class)
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
    .option(ChannelOption.TCP_NODELAY, true)
    .handler(new ClientInitializer());
```

### Pipeline direction

- Inbound events move from the pipeline head toward the tail.
- Outbound events move from the tail toward the head.
- Decoder and inbound business handlers belong on the inbound path.
- Encoder and outbound handlers belong on the outbound path.

### Buffer and codec basics

- `ByteBuf` replaces `ByteBuffer` in the common path. It keeps separate reader and writer indexes and does not require `flip()`.
- Use `ctx.alloc()` or `channel.alloc()` to allocate buffers that match the channel allocator.
- A codec usually does one transformation step.
  - `ByteToMessageDecoder`: bytes to higher-level messages
  - `MessageToByteEncoder<T>`: messages to bytes

## First safe commands

Dependency entrypoint:

```xml
<!-- Use netty-all for development convenience or individual modules for production.
     Recommended: 4.2.x.Final (current stable); 4.1.x.Final remains supported for existing deployments
     and is largely binary-compatible except for the changes listed in the Netty 4.2 migration guide. -->
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-all</artifactId>
    <version>${netty.version}</version>
</dependency>
```

TCP server skeleton:

```java
EventLoopGroup bossGroup = new NioEventLoopGroup(1);
EventLoopGroup workerGroup = new NioEventLoopGroup();
try {
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new EchoHandler());
            }
        });
    Channel server = bootstrap.bind(8080).sync().channel();
    server.closeFuture().sync();
} finally {
    bossGroup.shutdownGracefully().sync();
    workerGroup.shutdownGracefully().sync();
}
```

TCP client skeleton:

```java
EventLoopGroup group = new NioEventLoopGroup();
try {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(group)
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new ClientHandler());
            }
        });
    Channel channel = bootstrap.connect("localhost", 8080).sync().channel();
    channel.closeFuture().sync();
} finally {
    group.shutdownGracefully().sync();
}
```

UDP server skeleton:

`writeAndFlush` takes ownership of the new reference from `retainedDuplicate`; `SimpleChannelInboundHandler` auto-releases the original `DatagramPacket`.

```java
EventLoopGroup group = new NioEventLoopGroup();
try {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(group)
        .channel(NioDatagramChannel.class)
        .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                ctx.writeAndFlush(new DatagramPacket(packet.content().retainedDuplicate(), packet.sender()));
            }
        });
    Channel channel = bootstrap.bind(8080).sync().channel();
    channel.closeFuture().sync();
} finally {
    group.shutdownGracefully().sync();
}
```

## Representative patterns

### TCP echo pipeline

```java
final class EchoInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(new EchoHandler());
    }
}

final class EchoHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        ctx.writeAndFlush(msg.retainedDuplicate());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
```

### Length-prefixed codec chain

```java
final class MessagePipeline extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
            .addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4))
            .addLast(new MessageDecoder())
            .addLast(new MessageEncoder())
            .addLast(new BusinessHandler());
    }
}
```

### POJO-based pipeline

Separate byte-level concerns from business logic by decoding bytes into POJOs and encoding POJOs back to bytes:

```java
final class UnixTime {
    private final long value;

    UnixTime(long value) { this.value = value; }
    long value() { return value; }

    @Override
    public String toString() { return new Date((value - 2208988800L) * 1000).toString(); }
}

final class TimeDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() < 4) return;
        out.add(new UnixTime(in.readUnsignedInt()));
    }
}

final class TimeEncoder extends MessageToByteEncoder<UnixTime> {
    @Override
    protected void encode(ChannelHandlerContext ctx, UnixTime msg, ByteBuf out) {
        out.writeInt((int) msg.value());
    }
}

final class TimeHandler extends SimpleChannelInboundHandler<UnixTime> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, UnixTime msg) {
        System.out.println(msg);
        ctx.close();
    }
}
```

Pipeline assembly order: `TimeDecoder` (bytes to POJO, inbound) → `TimeHandler` (business logic with POJOs) → `TimeEncoder` (POJO to bytes, outbound).

### Manual `ByteBuf` ownership

```java
final class ManualReleaseHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        try {
            process(buf);
        } finally {
            buf.release();
        }
    }

    private void process(ByteBuf buf) {
        buf.readableBytes();
    }
}
```

### Write-then-close pattern

Use `ChannelFutureListener.CLOSE` when a handler must close the channel after a write completes:

```java
final class TimeServerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ByteBuf time = ctx.alloc().buffer(4);
        time.writeInt((int) (System.currentTimeMillis() / 1000L + 2208988800L));
        ctx.writeAndFlush(time).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
```

## Decision points

| Decision | Default | Escalate when |
| --- | --- | --- |
| TCP vs UDP | TCP for ordered reliable streams and backpressure-friendly request/response flow | UDP if the protocol is datagram-oriented, per-packet boundaries matter, or loss and reordering are acceptable |
| `ServerBootstrap` vs `Bootstrap` | `ServerBootstrap` for listening sockets | `Bootstrap` for clients and UDP |
| NIO vs native transport | NIO first | open [`native-transport.md`](./references/native-transport.md) for platform-specific tuning |
| auto-read vs manual flow control | `AUTO_READ = true` (default) for standard servers | open [`backpressure.md`](./references/backpressure.md) when the consumer cannot keep up with the producer rate or bounded memory is required |
| simple handler vs codec chain | simple handler when frames already exist | open [`framing.md`](./references/framing.md) or [`custom-codec.md`](./references/custom-codec.md) when bytes must become messages |
| `SimpleChannelInboundHandler` vs manual release | `SimpleChannelInboundHandler` for one inbound type | open [`reference-counting.md`](./references/reference-counting.md) when ownership is shared or asynchronous |

TCP and UDP imply different Netty shapes:

| Topic | TCP default | UDP default |
| --- | --- | --- |
| channel class | `NioServerSocketChannel` or `NioSocketChannel` | `NioDatagramChannel` |
| startup call | server `bind(...)`, client `connect(...)` | usually `bind(...)` for servers and `connect(...)` only when fixing a peer |
| event loop shape | server uses boss + worker groups, client usually one group | usually one group is enough |
| message model | stream-oriented, so framing is usually required | packet-oriented, so each `DatagramPacket` already carries message boundaries |
| pipeline concern | framing and codec ordering matter early | sender and recipient handling matter more than stream framing |

## Validation checklist

- [ ] bootstrap uses the right channel class for the chosen transport
- [ ] server bootstrap uses `option(...)` for the listening socket and `childOption(...)` for accepted channels when socket tuning is needed
- [ ] server code has separate boss and worker groups
- [ ] pipeline order matches framing, decoding, business logic, and encoding flow
- [ ] every owned `ByteBuf` is released exactly once
- [ ] shutdown path calls `shutdownGracefully()` for every event loop group
- [ ] if `AUTO_READ` is `false`, `channel.read()` is called in `channelActive()` and after each processed message

## Common pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| doing blocking I/O or CPU-heavy work on the event loop | one slow handler stalls unrelated channel progress on the same loop | offload blocking or expensive work before the pipeline backs up |
| assuming `ByteBuf` behaves like `ByteBuffer` | reader and writer indexes diverge from `ByteBuffer` habits and `flip()` does not exist | use `readerIndex()` and `writerIndex()` semantics directly |
| using `option(...)` when the intent is to tune accepted sockets | the setting lands on the listening socket instead of child channels | keep server-socket tuning on `option(...)` and accepted-channel tuning on `childOption(...)` |
| reusing a mutable handler without proving sharability | channel state leaks across pipelines and concurrency bugs appear | create one handler per pipeline unless `@Sharable` is truly safe |
| mixing framing, decoding, and business logic in one handler too early | ownership and ordering bugs become hard to reason about | separate framing, decode or encode, and business handlers by responsibility |
| retaining or forwarding buffers without an ownership rule | retain or release mismatches create leaks or use-after-release bugs | decide who owns the message at each boundary and document the handoff |

## Blocker references

Open these only when the common path is no longer enough:

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

## Output contract

Return:

1. the requested Netty server, client, handler, or codec code
2. the chosen transport and pipeline shape
3. the ownership and shutdown reasoning
4. any blocker references still required
