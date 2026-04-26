---
title: Custom Codec Development
description: Open this when a Netty protocol needs custom decoding, encoding, stateful parsing, or handler sharability decisions.
---

## Open this when

Open this when built-in framing is not enough, a protocol is stateful, or a pipeline needs a custom encoder or decoder between bytes and business messages.

## Choose the codec shape

| Need | Use |
| --- | --- |
| bytes to messages | `ByteToMessageDecoder` |
| messages to bytes | `MessageToByteEncoder<T>` |
| message to message translation | `MessageToMessageCodec<IN, OUT>` |

## Framing prerequisite

Length-prefixed and other framing choices belong in [framing.md](./framing.md). This file covers what comes after framing: stateful decode logic, encode patterns, and handler sharability.

## Stateful decoder pattern

Minimum JDK for this example: Java 17. It uses `case ... ->` switch rules.

```java
final class CommandDecoder extends ByteToMessageDecoder {
    private enum State {
        READ_HEADER,
        READ_PAYLOAD
    }

    private State state = State.READ_HEADER;
    private int payloadLength;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        for (;;) {
            switch (state) {
                case READ_HEADER -> {
                    if (in.readableBytes() < 4) {
                        return;
                    }
                    payloadLength = in.readInt();
                    state = State.READ_PAYLOAD;
                }
                case READ_PAYLOAD -> {
                    if (in.readableBytes() < payloadLength) {
                        return;
                    }
                    byte[] payload = new byte[payloadLength];
                    in.readBytes(payload);
                    out.add(new Command(payload));
                    state = State.READ_HEADER;
                }
            }
        }
    }
}
```

## `ReplayingDecoder` for simple protocols

When the protocol has a fixed structure and you want to avoid manual readable-bytes checks, use `ReplayingDecoder`. It throws a buffered `Error` (caught internally by Netty) when not enough bytes are available, then retries when more data arrives.

```java
final class TimeDecoder extends ReplayingDecoder<Void> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        out.add(new UnixTime(in.readUnsignedInt()));
    }
}
```

> [!WARNING]
> `ReplayingDecoder` is slightly slower than `ByteToMessageDecoder` due to try/catch overhead. Use it for readability on simple protocols; switch to `ByteToMessageDecoder` for hot paths where decode throughput matters.

## Stateful decoder with `handlerAdded` / `handlerRemoved`

Stateful handlers that allocate buffers or external resources must initialize them in `handlerAdded` and clean up in `handlerRemoved`:

```java
final class CumulativeDecoder extends ByteToMessageDecoder {
    private ByteBuf buf;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        buf = ctx.alloc().buffer(4);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        buf.release();
        buf = null;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        buf.writeBytes(in, in.readableBytes());
        if (buf.readableBytes() >= 4) {
            out.add(buf.readBytes(4));
        }
    }
}
```

## Encoder pattern

```java
final class CommandEncoder extends MessageToByteEncoder<Command> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Command msg, ByteBuf out) {
        out.writeInt(msg.payload().length);
        out.writeBytes(msg.payload());
    }
}
```

## Sharability rule

- A stateful decoder is not `@Sharable`.
- A stateless metrics or logging handler may be `@Sharable`.

```java
@Sharable
final class MetricsHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ctx.fireChannelRead(msg);
    }
}
```

## When to step back to framing first

If the real problem is message boundaries rather than object mapping, open [framing.md](./framing.md) first and keep the codec itself smaller.
