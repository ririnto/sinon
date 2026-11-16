# Transport and Pipeline Selection

## Decision points

| Decision | Default | Escalate when |
| --- | --- | --- |
| TCP vs UDP | TCP for ordered reliable streams and backpressure-friendly request/response flow | UDP if the protocol is datagram-oriented, per-packet boundaries matter, or loss and reordering are acceptable |
| `ServerBootstrap` vs `Bootstrap` | `ServerBootstrap` for listening sockets | `Bootstrap` for clients and UDP |
| NIO vs native transport | NIO first | open [`native-transport.md`](./native-transport.md) for platform-specific tuning |
| auto-read vs manual flow control | `AUTO_READ = true` (default) for standard servers | open [`backpressure.md`](./backpressure.md) when the consumer cannot keep up with the producer rate or bounded memory is required |
| simple handler vs codec chain | simple handler when frames already exist | open [`framing.md`](./framing.md) or [`custom-codec.md`](./custom-codec.md) when bytes must become messages |
| `SimpleChannelInboundHandler` vs manual release | `SimpleChannelInboundHandler` for one inbound type | open [`reference-counting.md`](./reference-counting.md) when ownership is shared or asynchronous |

TCP and UDP imply different Netty shapes:

| Topic | TCP default | UDP default |
| --- | --- | --- |
| channel class | `NioServerSocketChannel` or `NioSocketChannel` | `NioDatagramChannel` |
| startup call | server `bind(...)`, client `connect(...)` | usually `bind(...)` for servers and `connect(...)` only when fixing a peer |
| event loop shape | server uses boss + worker groups, client usually one group | usually one group is enough |
| message model | stream-oriented, so framing is usually required | packet-oriented, so each `DatagramPacket` already carries message boundaries |
| pipeline concern | framing and codec ordering matter early | sender and recipient handling matter more than stream framing |
