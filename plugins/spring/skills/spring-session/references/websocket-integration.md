# Spring Session WebSocket integration

Open reference when expiring or invalidating the backing session must terminate or invalidate related WebSocket or STOMP activity.

## Session-aware broker configuration

```java
@Configuration
@EnableScheduling
@EnableWebSocketMessageBroker
class WebSocketConfig extends AbstractSessionWebSocketMessageBrokerConfigurer<Session> {
    @Override
    protected void configureStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("https://app.example.com")
            .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue/", "/topic/");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
```

Key changes from the standard `WebSocketMessageBrokerConfigurer`:

- Extend `AbstractSessionWebSocketMessageBrokerConfigurer<Session>` instead of implementing `WebSocketMessageBrokerConfigurer`.
- Rename `registerStompEndpoints` to `configureStompEndpoints`.

What `AbstractSessionWebSocketMessageBrokerConfigurer` does behind the scenes:

- Adds `WebSocketConnectHandlerDecoratorFactory` as a `WebSocketHandlerDecoratorFactory` so a custom `SessionConnectEvent` fires with the `WebSocketSession`.
- Adds `SessionRepositoryMessageInterceptor` as a `HandshakeInterceptor` so the session is available in WebSocket properties and the last accessed time updates on handshake.
- Adds `SessionRepositoryMessageInterceptor` as a `ChannelInterceptor` on inbound channels so every received message updates the session's last accessed time.
- Registers `WebSocketRegistryListener` to maintain a mapping of session ids to WebSocket connections so they can be closed when the session expires.

Session timeout configuration for testing:

```yaml
server:
  servlet:
    session:
      timeout: 1m
```

Wait at most two minutes before verifying WebSocket closure. Spring Session expires after the configured timeout, but the Redis notification is not guaranteed within that window. A background task runs every minute that forcibly cleans up expired sessions.

Only messages sent from a user keep the session alive. Received messages do not renew session expiration.

## Decision points

| Situation | Use |
| --- | --- |
| STOMP or WebSocket activity must close when HTTP session expires | Spring Session WebSocket integration |
| Administrators need principal-based disconnect behavior | indexed lookup integration |
| Browser-origin WebSocket handshakes need origin checks | explicit allowed origins or origin patterns |

## Gotchas

- Do not treat WebSocket integration as automatic just because HTTP sessions are already stored in Redis or JDBC. The dedicated bridge must be wired explicitly.
- Do not skip origin and principal checks on the handshake path.
- Do not assume session expiration events reach messaging infrastructure without the dedicated Spring Session WebSocket bridge.
- Do not forget `@EnableScheduling`. The background cleanup task depends on scheduling support.
