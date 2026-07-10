# Redis advanced session configuration

Open reference when Redis repository type, JSON serialization, session events, listener bridging, session mapper, expiration-store behavior, or principal-indexed lookup need customization beyond the ordinary Redis path.

## RedisSessionRepository vs RedisIndexedSessionRepository

- Use `RedisSessionRepository` when the application only needs normal read, write, and expiration behavior for each session.
  - This is the default when `spring.session.data.redis.repository-type=default`.
- Use `RedisIndexedSessionRepository` when Spring Security, administration, or messaging needs `findByPrincipalName(...)`, session lifecycle events, or concurrent-session control.
  - This is selected when `spring.session.data.redis.repository-type=indexed`.

For the Boot-managed path, prefer the property above.
Use annotations only in a deliberately manual configuration that replaces Boot's session auto-configuration.

Default manual repository:

```java
@EnableRedisHttpSession
class DefaultRedisSessionConfiguration {
}
```

Indexed manual repository:

```java
@EnableRedisIndexedHttpSession
class IndexedRedisSessionConfiguration {
}
```

Application properties:

```yaml
spring:
  session:
    data:
      redis:
        repository-type: indexed
```

### RedisIndexedSessionRepository with Spring Security

Use the `SpringSessionBackedSessionRegistry` common-path configuration in `SKILL.md` so clustered concurrent-session control reads the same indexed repository as Spring Session.

`SpringSessionBackedSessionRegistry` does not implement `getAllPrincipals()` because Spring Session cannot enumerate every principal; Spring Security's concurrent-session control does not require that method.

## JSON serialization

The default serializer is Java serialization.
Switch to JSON when multiple applications share the Redis instance or when class evolution between deployments makes default serialization fragile.

```java
@Configuration
class SessionConfig {
    @Bean
    RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer();
    }
}
```

## Session events

Indexed Redis sessions publish `SessionCreatedEvent`, `SessionDeletedEvent`, `SessionDestroyedEvent`, and `SessionExpiredEvent`.
React to them with a Spring application listener:

```java
@Component
class SessionLifecycleListener {
    @EventListener
    void onSessionCreated(SessionCreatedEvent event) {
        log.info("Session created: {}", event.getSessionId());
    }
    @EventListener
    void onSessionExpired(SessionExpiredEvent event) {
        log.info("Session expired: {}", event.getSessionId());
    }
    @EventListener
    void onSessionDeleted(SessionDeletedEvent event) {
        log.info("Session deleted: {}", event.getSessionId());
    }
}
```

For servlet `HttpSessionListener` bridging, declare `SessionEventHttpSessionListenerAdapter` alongside registered listeners:

```java
@Bean
SessionEventHttpSessionListenerAdapter sessionEventAdapter() {
    return new SessionEventHttpSessionListenerAdapter(List.of(new MyHttpSessionListener()));
}
```

## Customizing the session mapper

`RedisSessionMapper` controls how session attributes map to Redis hash fields.
Provide a custom bean to change the attribute-to-field strategy:

```java
@Bean
RedisSessionMapper redisSessionMapper() {
    return new RedisSessionMapper(customDeltaUpdateStrategy());
}
```

## Customizing the expiration store

`RedisSessionExpirationStore` manages how and when session keys expire in Redis.
Provide a custom bean to change expiration behavior:

```java
@Bean
RedisSessionExpirationStore redisSessionExpirationStore(RedisIndexedSessionRepository repository) {
    return new SortedSetRedisSessionExpirationStore(repository);
}
```

## Principal-based lookup and administration

Use `FindByIndexNameSessionRepository` directly when the application needs explicit session administration beyond Spring Security's concurrency controls:

```java
@Service
class SessionAdministrationService {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    SessionAdministrationService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    void expireAllSessionsFor(String username) {
        Map<String, ? extends Session> active = sessions.findByPrincipalName(username);
        active.keySet().forEach(sessions::deleteById);
    }

    Map<String, ? extends Session> activeSessions(String username) {
        return sessions.findByPrincipalName(username);
    }
}
```

### Indexed session security context name

By default, indexed lookup uses `FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME` which expects `SecurityContextHolder` to be populated.
The session attribute key is `spring.security.context`.
For custom authentication, set the principal index name attribute on the session:

```java
session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, authentication.getName());
```

## Gotchas

- Do not switch to the indexed repository just to get events.
  - Use explicit event handling only when the application has a concrete listener that acts on session lifecycle transitions.
- Do not mix serialization strategies across applications sharing the same Redis namespace.
  - Pick one and enforce it in every application.
- Do not assume `SessionExpiredEvent` fires at the exact moment of TTL expiry.
  - Redis expiration is approximate and events may arrive with delay.
- Do not use the in-memory Spring Security `SessionRegistryImpl` when concurrent-session limits must work across nodes.
  - Inject `SpringSessionBackedSessionRegistry` into `maximumSessions()` instead.
