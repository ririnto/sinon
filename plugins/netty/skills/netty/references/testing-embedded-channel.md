---
title: Testing with EmbeddedChannel
description: Open this when Netty handlers, codecs, or pipelines must be tested without starting real sockets.
---

## Open this when

Open this when you need fast unit tests for handler behavior, framing, encoding, or exception paths and do not want to bind a real port.

## Inbound test

```java
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;

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
import io.netty.buffer.Unpooled;

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

## Exception path test

Only 2 bytes arrive instead of the required 4 — no frame is produced and no exception is thrown from the decoder. `writeInbound` returns `false` because the decoder cannot produce a complete frame. `finish()` releases pending internal buffers and returns `true` if any remain.

```java
import io.netty.buffer.Unpooled;

@Test
void propagatesExceptionOnShortFrame() {
    EmbeddedChannel channel = new EmbeddedChannel(
        new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4)
    );
    ByteBuf shortInput = Unpooled.buffer(2);
    shortInput.writeShort(42);

    assertFalse(channel.writeInbound(shortInput));
    assertTrue(channel.finish());
}
```

## Invariants

- call `finish()` so queued messages and buffers are released
- assert both inbound and outbound sides explicitly when the handler transforms messages
- use `assertThrowsExactly(...)` for exception paths — verify that malformed input either produces no output or triggers the expected error response through the pipeline
- release outbound `ByteBuf` values read from `readOutbound()` after assertion
- if a handler manually owns `ByteBuf`, assert the reference count reaches zero when the test completes
