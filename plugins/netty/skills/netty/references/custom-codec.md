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

## Length-prefixed decoding

Payload shaped as `[length:4][payload:*]`:

```java
pipeline.addLast(new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
```

Payload shaped as `[magic:2][length:4][payload:*]`:

```java
pipeline.addLast(new LengthFieldBasedFrameDecoder(1048576, 2, 4, 0, 6));
```

## Stateful decoder pattern

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
