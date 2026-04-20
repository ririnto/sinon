---
title: Backpressure and Flow Control
description: Open this when a Netty handler must control inbound read rate, apply flow-controlled consumption, or manage AUTO_READ behavior.
---

## Open this when

Open this when a consumer cannot keep up with the producer rate, batch processing requires explicit read signals, or protocol-level flow control (credit-based, windowed) must be implemented.

## `AUTO_READ` defaults and manual read

By default, `ChannelOption.AUTO_READ` is `true`. Netty automatically reads all available data from the socket and delivers it to the pipeline as fast as the event loop can process it. This is correct for most servers.

When the consumer is slower than the producer, set `AUTO_READ` to `false` and call `channel.read()` explicitly after each message is processed:

```java
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(bossGroup, workerGroup)
    .channel(NioServerSocketChannel.class)
    .childOption(ChannelOption.AUTO_READ, false)
    .childHandler(new FlowControlledInitializer());
```

Manual-read handler (request the next read only after processing completes; trigger the first read in `channelActive` since `AUTO_READ` is off):

```java
final class FlowControlledHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        process(msg);
        ctx.channel().read();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.channel().read();
    }

    private void process(ByteBuf buf) {
        buf.readableBytes();
    }
}
```

## When to use manual read

| Scenario | Use manual read? | Why |
| --- | --- | --- |
| standard request/response server | No | default AUTO_READ keeps latency low |
| slow consumer, bounded memory | Yes | prevents unbounded inbound queue growth |
| protocol with credit-based flow control | Yes | reads only when the sender has credit |
| batch processing with backpressure propagation | Yes | each batch completion signals the next read |

## Interaction with framing decoders

When `AUTO_READ` is `false`, framing decoders such as `LengthFieldBasedFrameDecoder` still accumulate bytes internally. They call `decode()` on each `read()` invocation and produce complete frames when enough data has arrived. The manual `channel.read()` call controls when the decoder is fed, not whether the internal buffer accumulates.

```java
ch.pipeline()
    .addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4))
    .addLast(new MessageDecoder())
    .addLast(new FlowControlledBusinessHandler());
```

`FlowControlledBusinessHandler` calls `ctx.channel.read()` after each message.

## Common mistakes

- setting `AUTO_READ` to `false` and forgetting to call `channel.read()` in `channelActive()` — the channel never reads anything
- calling `channel.read()` inside `channelRead()` before processing completes — defeats the purpose of flow control by re-entering immediately
- mixing `AUTO_READ` handlers in the same pipeline — the option is per-channel, not per-handler; all handlers share the same auto-read state
