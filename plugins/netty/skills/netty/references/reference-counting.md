---
title: Reference Counting and Memory Ownership
description: Open this when a Netty handler must retain, release, pass downstream, or debug leaked ByteBuf instances.
---

## Open this when

Open this when you are seeing `IllegalReferenceCountException`, leak detector output, asynchronous handoff of `ByteBuf`, or uncertainty about who owns a message after a handler touches it.

## Ownership rules

| Situation | Must release? | Why |
| --- | --- | --- |
| `channelRead0` in `SimpleChannelInboundHandler<T>` | No, unless you retained it | Netty auto-releases after the method returns |
| `channelRead` in `ChannelInboundHandlerAdapter` | Yes | you received ownership |
| `ctx.fireChannelRead(msg)` without keeping a copy | No | ownership moves downstream |
| store a message for async work | Yes, after async work finishes | you kept ownership beyond the callback |
| write a retained duplicate | Yes, if you created an extra retained reference | the extra reference is yours |

## Safe release patterns

Manual release in `ChannelInboundHandlerAdapter`:

```java
final class BusinessHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        try {
            consume(buf);
        } finally {
            buf.release();
        }
    }

    private void consume(ByteBuf buf) {
        buf.readableBytes();
    }
}
```

Pass downstream from `SimpleChannelInboundHandler`:

```java
final class ForwardingHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        ctx.fireChannelRead(msg.retain());
    }
}
```

Keep a buffer for real off-thread async work:

```java
final class AsyncHandler extends ChannelInboundHandlerAdapter {
    private final Executor executor;

    AsyncHandler(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = ((ByteBuf) msg).retain();
        executor.execute(() -> {
            try {
                useLater(buf);
            } finally {
                buf.release();
                ((ByteBuf) msg).release();
            }
        });
    }

    private void useLater(ByteBuf buf) {
        buf.readableBytes();
    }
}
```

Use a real external executor for blocking or CPU-heavy work. If you stay on the channel's event loop, you did not actually offload anything.

If you do not need both references, do not retain. Move ownership once and release once.

## Leak detection

JVM flag:

```text
-Dio.netty.leakDetection.level=advanced
```

Programmatic configuration:

```java
ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);
```

Levels:

| Level | Use |
| --- | --- |
| `SIMPLE` | everyday development |
| `ADVANCED` | targeted leak debugging |
| `PARANOID` | test suites or short-lived diagnosis |

## Common mistakes

- releasing twice after a downstream handler already owns the message
- forgetting to release on exception paths
- calling `fireChannelRead(msg)` from `channelRead0` without `retain()`
- storing `ByteBuf` in a field and never releasing it on completion or close
