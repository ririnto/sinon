# Dependency and Transport Examples

## Java syntax baseline for examples

- Netty 4.x itself supports older JDKs, but reference examples in this skill explicitly call out when they rely on Java 17+ syntax such as `instanceof` pattern variables or arrow-form switch rules.
- When that note is absent, treat the example as ordinary Netty API guidance rather than a Java-version-specific syntax showcase.

Dependency entrypoint:

Use `netty-all` for development convenience or individual modules for production.
For new work, use the current stable Netty line compatible with the project's Java version and dependency platform.
Use these 4.2.x.Final examples only when the selected version supports their APIs.
Keep existing 4.1.x.Final deployments on their managed line and consult the Netty 4.2 migration guide before moving them.

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-all</artifactId>
    <version>${netty.version}</version>
</dependency>
```

TCP server skeleton:

```java
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
try {
    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new EchoHandler());
            }
        });
    Channel server = bootstrap.bind(8080).sync().channel();
    server.closeFuture().sync();
} finally {
    bossGroup.shutdownGracefully().sync();
    workerGroup.shutdownGracefully().sync();
}
```

TCP client skeleton:

```java
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

EventLoopGroup group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
try {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(group)
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ch.pipeline().addLast(new ClientHandler());
            }
        });
    Channel channel = bootstrap.connect("localhost", 8080).sync().channel();
    channel.closeFuture().sync();
} finally {
    group.shutdownGracefully().sync();
}
```

UDP server skeleton:

`writeAndFlush` takes ownership of the new reference from `retainedDuplicate`.
`SimpleChannelInboundHandler` auto-releases the original `DatagramPacket`.

```java
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;

EventLoopGroup group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
try {
    Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(group)
        .channel(NioDatagramChannel.class)
        .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
                ctx.writeAndFlush(new DatagramPacket(packet.content().retainedDuplicate(), packet.sender()));
            }
        });
    Channel channel = bootstrap.bind(8080).sync().channel();
    channel.closeFuture().sync();
} finally {
    group.shutdownGracefully().sync();
}
```

## Bootstrap options

```java
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(bossGroup, workerGroup)
    .channel(NioServerSocketChannel.class)
    .option(ChannelOption.SO_BACKLOG, 256)
    .childOption(ChannelOption.SO_KEEPALIVE, true)
    .childOption(ChannelOption.TCP_NODELAY, true)
    .childHandler(new EchoInitializer());

Bootstrap clientBootstrap = new Bootstrap();
clientBootstrap.group(group)
    .channel(NioSocketChannel.class)
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
    .option(ChannelOption.TCP_NODELAY, true)
    .handler(new ClientInitializer());
```
