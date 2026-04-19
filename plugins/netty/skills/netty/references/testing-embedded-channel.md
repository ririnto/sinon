---
title: Testing with EmbeddedChannel
description: Open this when Netty handlers, codecs, or pipelines must be tested without starting real sockets.
---

## Open this when

Open this when you need fast unit tests for handler behavior, framing, encoding, or exception paths and do not want to bind a real port.

## Inbound test

```java
@Test
void decodesOneLine() {
    EmbeddedChannel channel = new EmbeddedChannel(
        new LineBasedFrameDecoder(64),
        new StringDecoder(StandardCharsets.UTF_8)
    );
    ByteBuf input = Unpooled.copiedBuffer("hello\n", StandardCharsets.UTF_8);
    assertAll(
        () -> assertTrue(channel.writeInbound(input)),
        () -> assertEquals("hello", channel.readInbound()),
        () -> assertFalse(channel.finish())
    );
}
```

## Outbound test

```java
@Test
void encodesUppercase() {
    EmbeddedChannel channel = new EmbeddedChannel(new UppercaseEncoder());
    ByteBuf encoded = null;
    try {
        assertTrue(channel.writeOutbound("hello"));
        encoded = channel.readOutbound();
        ByteBuf finalEncoded = encoded;
        assertAll(
            () -> assertEquals("HELLO", finalEncoded.toString(StandardCharsets.UTF_8)),
            () -> assertFalse(channel.finish())
        );
    } finally {
        if (encoded != null) {
            encoded.release();
        }
    }
}

final class UppercaseEncoder extends MessageToByteEncoder<String> {
    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) {
        out.writeCharSequence(msg.toUpperCase(), StandardCharsets.UTF_8);
    }
}
```

## Pipeline test

```java
@Test
void decodesLengthPrefixedMessage() {
    EmbeddedChannel channel = new EmbeddedChannel(
        new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4),
        new MessageDecoder()
    );
    ByteBuf input = Unpooled.buffer();
    input.writeInt(5);
    input.writeCharSequence("hello", StandardCharsets.UTF_8);
    assertAll(
        () -> assertTrue(channel.writeInbound(input)),
        () -> assertEquals("hello", channel.<Message>readInbound().text()),
        () -> assertFalse(channel.finish())
    );
}
```

## Invariants

- call `finish()` so queued messages and buffers are released
- assert both inbound and outbound sides explicitly when the handler transforms messages
- use `assertThrowsExactly(...)` for exception paths
- release outbound `ByteBuf` values read from `readOutbound()` after assertion
- if a handler manually owns `ByteBuf`, assert the reference count reaches zero when the test completes
