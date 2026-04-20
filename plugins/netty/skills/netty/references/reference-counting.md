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

> [!NOTE]
> The manual release pattern for `ChannelInboundHandlerAdapter` is covered in the SKILL.md common path (`ManualReleaseHandler`). This reference focuses on ownership handoff patterns that go beyond single-handler release.

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

`ChannelInboundHandlerAdapter` does not auto-release — you own `msg` at `ref=1`. Retain so the executor has its own reference at `ref=2`. In the executor: release the executor's reference (`ref=2 → 1`), then release the original since `ChannelInboundHandlerAdapter` does not auto-release (`ref=1 → 0`).

```java
final class AsyncHandler extends ChannelInboundHandlerAdapter {
    private final Executor executor;

    AsyncHandler(Executor executor) {
        this.executor = executor;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf retained = ((ByteBuf) msg).retain();
        executor.execute(() -> {
            try {
                useLater(retained);
            } finally {
                retained.release();
                ((ByteBuf) msg).release();
            }
        });
    }

    private void useLater(ByteBuf buf) {
        buf.readableBytes();
    }
}
```

> [!WARNING]
> This pattern is correct only for `ChannelInboundHandlerAdapter`. If you extend `SimpleChannelInboundHandler` instead, it auto-releases after `channelRead0` returns, and releasing `msg` again in the executor causes an `IllegalReferenceCountException`. In that case, retain once and release only the retained reference in the executor.

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
