# Spring GraphQL transports and subscriptions

Open this reference when the ordinary HTTP server path in `SKILL.md` is not enough and the task needs subscriptions, WebSocket, SSE, or RSocket transport wiring.

## Transport decisions

- HTTP: default for queries and mutations.
  - Also supports SSE for subscription responses.
- SSE (Server-Sent Events): subscription-only transport over HTTP POST with `Accept: text/event-stream`.
  - Simpler than WebSocket.
    - No persistent connection.
  - Only subscriptions are supported over SSE.
    - Queries and mutations must use the plain JSON HTTP variant.
- WebSocket: use when the application genuinely needs subscriptions or long-lived interactive GraphQL sessions.
  - Supports queries, mutations, and subscriptions on a single persistent connection.
  - Requires `spring-boot-starter-websocket` for Servlet apps.
    - No extra starter for WebFlux.
- RSocket: use only when the surrounding system already standardizes on RSocket.

Keep one dominant transport unless a concrete subscription workflow requires more.

## Transport starter requirements

| Transport | Additional starter | Notes |
| --- | --- | --- |
| HTTP | `spring-boot-starter-webmvc` or `spring-boot-starter-webflux` | Always required |
| SSE | none (uses HTTP handler) | Built into `GraphQlHttpHandler` |
| WebSocket (Servlet) | `spring-boot-starter-websocket` | Required for Servlet-based apps |
| WebSocket (WebFlux) | none | Built into WebFlux |
| RSocket | `spring-boot-starter-rsocket` | Requires Spring WebFlux on Reactor Netty |

## HTTP endpoint configuration

```yaml
spring:
  graphql:
    http:
      path: /graphql
```

## SSE subscription shape

Clients send an HTTP POST with `Accept: text/event-stream` header.
The response is one or more Server-Sent Events.
Only subscription operations are supported over SSE.

```java
@Controller
class BookSubscriptionController {
    @SubscriptionMapping
    Flux<Book> bookAdded() {
        return bookEvents.stream();
    }
}
```

## WebSocket subscription endpoint

```yaml
spring:
  graphql:
    websocket:
      path: /graphql
```

Use the same GraphQL path unless deployment needs a separate WebSocket route.

## Subscription mapping shape

```graphql
type Subscription {
  bookAdded: Book!
}
```

```java
@Controller
class BookSubscriptionController {
    @SubscriptionMapping
    Flux<Book> bookAdded() {
        return bookEvents.stream();
    }
}
```

## RSocket route shape

```yaml
spring:
  graphql:
    rsocket:
      mapping: graphql
```

Use RSocket only when the surrounding platform already carries request-stream traffic over RSocket.

## WebSocket lifecycle interception

`WebSocketGraphQlInterceptor` extends `WebGraphQlInterceptor` with additional callbacks for WebSocket connection start/end and client-side subscription cancellation.
At most one `WebSocketGraphQlInterceptor` can exist in the interceptor chain.

## Decision points

| Situation | Use |
| --- | --- |
| Ordinary query or mutation API | HTTP only |
| Subscription stream, no persistent connection needed | SSE + `@SubscriptionMapping` |
| Subscription stream with bidirectional use | WebSocket + `@SubscriptionMapping` |
| Existing RSocket-native platform | RSocket transport |
| No concrete subscription need | stay on HTTP |

## Validation rule

Verify the chosen transport matches the deployed endpoint path and that subscription tests prove stream behavior on the same protocol clients will actually use.
