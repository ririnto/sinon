---
metadata:
  reference:
    Reactor Netty:
      version: 1.3.7
      url:
        - https://projectreactor.io/docs/netty/release/reference/about-doc.html
        - https://github.com/reactor/reactor-netty/releases/tag/v1.3.7
    Reactor Netty milestone:
      version: 1.4.0-M2
      url: https://github.com/reactor/reactor-netty/releases/tag/v1.4.0-M2
name: reactor-netty
description: >-
  Build or debug Reactor Netty HTTP, TCP, UDP, or QUIC clients and servers, including lifecycle and resource management.
---

# Reactor Netty

## Official Baseline

- Use the official Reactor Netty 1.3.x reference guide for this skill.
  The `v1.3.7` release and its 2025.0.7 Release Train are reviewed baselines, not dependency pins.
- Treat `v1.4.0-M2` as a milestone release, not a stable production version.
- Before adding or upgrading Reactor Netty, check `io.projectreactor:reactor-bom` and `io.projectreactor.netty:reactor-netty-http` in Maven Central for the latest stable compatible versions.
  Honor the project's existing platform, BOM, or pins, and keep Reactor Core and Reactor Netty on a compatible managed release train.
  Set `${reactor.bom.version}` to the selected release train only when the build does not manage Reactor dependencies.
- Confirm the target project's resolved Reactor Netty version before using version-sensitive APIs.
- Keep HTTP, TCP, UDP, and QUIC aligned with the Reactor Netty reference guide chapters for the same release line.

Complete the requested Reactor Netty change through its builder and reactive flow, preserving resource ownership and shutdown behavior.

## Goal

Keep the common path on Reactor Netty builders and reactive flow: `HttpServer`, `HttpClient`, `TcpServer`, `TcpClient`, `UdpServer`, `UdpClient`, `QuicServer`, and `QuicClient`.

- Treat `.handle((inbound, outbound) -> ...)` and HTTP route handlers as the main composition points.
- Do not block inside reactive handlers.
  - Use blocking only at process boundaries such as `bindNow()`, `connectNow()`, terminal response retrieval in top-level sample code, or `onDispose().block()`.
- Keep low-level Netty details out of the common path.
  - If the task needs `ChannelPipeline`, `ByteBuf.release()`, `ChannelFuture`, or custom codecs, use the lower-level Netty API model rather than forcing the builder surface.
- Dispose custom resources explicitly when you create them.

## When this skill fits

Use this skill for:

- reactive HTTP servers and clients
- reactive TCP, UDP, or QUIC servers and clients
- builder-based configuration with lifecycle callbacks such as `doOnBound`, `doOnConnected`, and `doOnConnection`
- resource-aware startup, warmup, and shutdown using Reactor Netty abstractions

Keep low-level Netty concerns out of this common path:

- `ServerBootstrap`, `Bootstrap`, `ChannelPipeline`, and handler ordering
- `ByteBuf` ownership and manual `release()`
- custom Netty codecs and frame decoders

## Task Context

Read the affected builder, handler, resource owner, and tests before changing transport or lifecycle behavior.
Reuse the project's dependency management and shared resources unless the task requires an authorized change.
Use the [reference table](#references) for resource, timeout, TLS, observability, or WebSocket details relevant to the task.
Examples do not authorize public listeners, external connections, deployed resource changes, or sensitive traffic capture.

## Core model

### Dependency entrypoint

Use `reactor-netty-core` for TCP or UDP only, `reactor-netty-http` for HTTP, and `reactor-netty-quic` for QUIC (`QuicServer` and `QuicClient`).
When configuring dependencies, read the [dependency example](references/dependency-example.md) and honor the managed release train.

### Lifecycle and composition

- Server builders return `DisposableServer` from `bindNow()`, except `UdpServer.bindNow()` returns `Connection`.
- Client builders return `Connection` from `connectNow()`.
- HTTP routes return reactive send operations.
- TCP and UDP handlers compose inbound and outbound streams with `Publisher` chains.
- Use lifecycle hooks such as `doOnBind`, `doOnBound`, `doOnChannelInit`, `doOnConnection`, `doOnConnected`, and `doOnDisconnected` only when they change startup, channel extension, or shutdown behavior.

### Response consumption and reactive errors

Use `.responseSingle((resp, content) -> ...)` for status and a fully buffered body (`Mono<T>`), and `.responseContent()` for streaming chunks (`Flux<ByteBuf>`).
Use `onErrorResume` when a handler converts a reactive failure into an HTTP response or fallback; otherwise let unexpected errors propagate.
Do not use blocking `try/catch` inside reactive lambdas: errors travel through the reactive error channel.
For examples of aggregated or streaming responses and error recovery, read [HTTP response and error examples](references/http-response-examples.md).

### Resource model

- Default loop resources are shared and usually sufficient for the common path.
- `warmup()` is optional but useful when startup latency matters.
- Use `.runOn(...)` only when the application truly needs custom loop resources.

### Operational entrypoints

- Use `.secure(...)` when the common path must switch to TLS.
- Use `.responseTimeout(...)` or channel options when a client must fail fast.
- Use wiretap only when traffic evidence is needed and the capture scope permits sensitive headers or payloads.
- Use `.metrics(true)` only when the application already has a metrics strategy.
- Open the blocker references when these concerns stop being one-line builder configuration.

## Examples

For HTTP, TCP, or UDP clients and servers, warmup, or lifecycle hook examples, read [transport examples](references/transport-examples.md).

## Decision points

Choose the builder for the protocol and use `.route(...)` for standard HTTP endpoints or `.handle(...)` for lower-level response composition.
Use default resources until isolation is needed and select TLS, timeouts, observability, or WebSocket references for those needs.

## Completion

Verify the affected behavior with the existing native tests, using real transport only when the contract requires it.
Check the relevant invariants below rather than testing every builder and protocol.

- [ ] selected builder matches the protocol being implemented
- [ ] bind/connect and disposal flow are explicit
- [ ] reactive handlers do not block internally
- [ ] lifecycle hooks are attached only where they affect behavior
- [ ] low-level Netty pipeline or buffer ownership details are not required for the common path
- [ ] error handling uses reactive operators such as `onErrorResume` or explicit response composition rather than blocking try/catch inside lambdas
- [ ] response consumption shape (`.responseSingle()` vs `.responseContent()`) matches the use case

## References

Open these advanced references when the common path is not enough:

| Blocker | Open |
| --- | --- |
| custom loop resources, shared providers, or explicit disposal ordering | [event-loop-and-resources.md](./references/event-loop-and-resources.md) |
| response timeout, connect timeout, retry, or connection pool tuning | [timeouts-and-pool-tuning.md](./references/timeouts-and-pool-tuning.md) |
| HTTPS, custom trust, or mTLS | [ssl-tls.md](./references/ssl-tls.md) |
| wiretap, metrics, or access logging | [metrics-and-observability.md](./references/metrics-and-observability.md) |
| WebSocket client or server flow | [websocket.md](./references/websocket.md) |

## Result

Complete the authorized change and explain its builder, response, resource, or shutdown consequences.
Report the checks run and any unverified transport or operational behavior.
