# Pipeline Examples

## Java syntax baseline for examples

- Netty 4.x itself supports older JDKs, but reference examples in this skill explicitly call out when they rely on Java 17+ syntax such as `instanceof` pattern variables or arrow-form switch rules.
- When that note is absent, treat the example as ordinary Netty API guidance rather than a Java-version-specific syntax showcase.

### TCP echo pipeline

```java
final class EchoInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline().addLast(new EchoHandler());
    }
}

final class EchoHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        ctx.writeAndFlush(msg.retainedDuplicate());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
```

## Length-prefixed codec chain

```java
final class MessagePipeline extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
            .addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4))
            .addLast(new MessageDecoder())
            .addLast(new MessageEncoder())
            .addLast(new BusinessHandler());
    }
}
```

## POJO-based pipeline

Separate byte-level concerns from business logic by decoding bytes into POJOs and encoding POJOs back to bytes:

```java
final class UnixTime {
    private final long value;

    UnixTime(long value) { this.value = value; }
    long value() { return value; }

    @Override
    public String toString() { return new Date((value - 2208988800L) * 1000).toString(); }
}

final class TimeDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() >= 4) {
            out.add(new UnixTime(in.readUnsignedInt()));
        }
    }
}

final class TimeEncoder extends MessageToByteEncoder<UnixTime> {
    @Override
    protected void encode(ChannelHandlerContext ctx, UnixTime msg, ByteBuf out) {
        out.writeInt((int) msg.value());
    }
}

final class TimeHandler extends SimpleChannelInboundHandler<UnixTime> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, UnixTime msg) {
        System.out.println(msg);
        ctx.close();
    }
}
```

Pipeline assembly order: `TimeDecoder` (bytes to POJO, inbound) → `TimeHandler` (business logic with POJOs) → `TimeEncoder` (POJO to bytes, outbound).

## Manual `ByteBuf` ownership

```java
final class ManualReleaseHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buf = (ByteBuf) msg;
        try {
            process(buf);
        } finally {
            buf.release();
        }
    }

    private void process(ByteBuf buf) {
        buf.readableBytes();
    }
}
```

## Write-then-close pattern

Use `ChannelFutureListener.CLOSE` when a handler must close the channel after a write completes:

```java
final class TimeServerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ByteBuf time = ctx.alloc().buffer(4);
        time.writeInt((int) (System.currentTimeMillis() / 1000L + 2208988800L));
        ctx.writeAndFlush(time).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
```
