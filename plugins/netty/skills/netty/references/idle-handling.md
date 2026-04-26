---
title: Idle Handling and Heartbeats
description: Open this when a Netty connection needs read-idle, write-idle, or heartbeat handling.
---

## Open this when

Open this when long-lived TCP connections must close stale peers, send heartbeat frames, or distinguish reader and writer idleness.

## Attach `IdleStateHandler`

```java
pipeline.addLast(new IdleStateHandler(30, 60, 0, TimeUnit.SECONDS));
pipeline.addLast(new HeartbeatHandler());
```

## React to idle events

Minimum JDK for this example: Java 17. It uses `instanceof` pattern matching and `case ... ->` switch rules.

```java
final class HeartbeatHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent event) {
            switch (event.state()) {
                case READER_IDLE -> ctx.close();
                case WRITER_IDLE -> ctx.writeAndFlush(new PingFrame());
                case ALL_IDLE -> ctx.close();
            }
        } else {
            ctx.fireUserEventTriggered(evt);
        }
    }
}
```

## Guidance

- place `IdleStateHandler` before the handler that reacts to the event
- close on reader idle when stale peers are not acceptable
- send heartbeat frames only if the protocol defines them explicitly
