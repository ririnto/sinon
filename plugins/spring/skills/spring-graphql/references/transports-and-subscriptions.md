# Spring GraphQL transports and subscriptions

Open this reference when the ordinary HTTP server path in `SKILL.md` is not enough and the task needs subscriptions, WebSocket, or RSocket transport wiring.

## Transport decisions

- HTTP: default for queries and mutations.
- WebSocket: use when the application genuinely needs subscriptions or long-lived interactive GraphQL sessions.
- RSocket: use only when the surrounding system already standardizes on RSocket.

Keep one dominant transport unless a concrete subscription workflow requires more.

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

## Decision points

| Situation | Use |
| --- | --- |
| Ordinary query or mutation API | HTTP only |
| Subscription stream for clients | WebSocket + `@SubscriptionMapping` |
| Existing RSocket-native platform | RSocket transport |
| No concrete subscription need | stay on HTTP |

## Validation rule

Verify the chosen transport matches the deployed endpoint path and that subscription tests prove stream behavior on the same protocol clients will actually use.
