# Spring Session WebSocket integration

Open this reference when WebSocket or STOMP traffic must observe the same session lifecycle as HTTP traffic.

Use Spring Session messaging integration when expiring or invalidating the backing session must also terminate or invalidate related messaging activity.

## WebSocket endpoint baseline

```java
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("https://app.example.com")
            .withSockJS();
    }
}
```

## Session-aware broker configuration

```java
@Configuration
class WebSocketSessionConfig extends AbstractSessionWebSocketMessageBrokerConfigurer<Session> {
}
```

## Decision points

| Situation | Use |
| --- | --- |
| STOMP or WebSocket activity must close when the HTTP session expires | Spring Session WebSocket integration |
| Administrators need principal-based disconnect behavior | combine indexed session lookup with messaging integration |
| Browser-origin WebSocket handshakes need origin checks | explicit allowed origins or origin patterns |

## Gotchas

- Do not treat WebSocket integration as automatic just because HTTP sessions are already stored in Redis or JDBC.
- Do not skip origin and principal checks on the handshake path.
- Do not assume session expiration events reach messaging infrastructure without the dedicated Spring Session WebSocket bridge.
