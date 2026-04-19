---
title: Frame Decoders and Message Boundaries
description: Open this when a Netty byte stream must be split into complete messages before business handlers run.
---

## Open this when

Open this when TCP stream boundaries are the real problem and the application needs length-based, delimiter-based, or fixed-size framing before decoding business messages.

## Length-field framing

```java
pipeline.addLast(new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
```

Use this for payloads shaped like `[length][payload]`.

## Delimiter-based framing

```java
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
