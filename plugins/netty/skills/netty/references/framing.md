---
title: Frame Decoders and Message Boundaries
description: Open this when a Netty byte stream must be split into complete messages before business handlers run.
---

## Open this when

Open when TCP stream boundaries are the real problem and the application needs length-based, delimiter-based, or fixed-size framing before decoding business messages.

## Why framing is required for TCP

TCP is a stream protocol, not a message protocol. The operating system delivers bytes to the application in arbitrary chunks regardless of how the sender called `write()`. Two independent `write()` calls on the sender side can arrive as one merged buffer or split across multiple `read()` callbacks on the receiver. A framing decoder reassembles these byte fragments into complete, meaningful message boundaries before business handlers see them.

Without framing, a handler that expects "one message per `channelRead`" call will encounter fragmented messages (only part of a logical message arrives) or merged messages (parts of two consecutive logical messages in one buffer).

## Length-field framing

```java
pipeline.addLast(new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
```

Use this for payloads shaped like `[length][payload]`.

## Delimiter-based framing

```java
import io.netty.buffer.Unpooled;

pipeline.addLast(new DelimiterBasedFrameDecoder(
    8192,
    Unpooled.copiedBuffer("\r\n", StandardCharsets.UTF_8),
    Unpooled.copiedBuffer("\n", StandardCharsets.UTF_8)
));
```

Use this for line-oriented text protocols.

## Fixed-size framing

```java
pipeline.addLast(new FixedLengthFrameDecoder(128));
```

Use this only when every frame has the same byte width.

## Guidance

- put framing before custom decoders and business handlers
- if the protocol has variable structure after framing, continue with [custom-codec.md](./custom-codec.md)
- do not parse partial business messages in the application handler when framing belongs in the pipeline
