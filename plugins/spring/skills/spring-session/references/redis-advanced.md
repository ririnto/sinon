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

Use the `SpringSessionBackedSessionRegistry` configuration from the ordinary Spring Security session setup so clustered concurrent-session control reads the same indexed repository as Spring Session.

`SpringSessionBackedSessionRegistry` does not implement `getAllPrincipals()` because Spring Session cannot enumerate every principal.
Spring Security's concurrent-session control does not require that method.

## JSON serialization

The default serializer is Java serialization.
Consider JSON when multiple applications share the Redis instance or class evolution makes Java serialization fragile. Test the serializer and mapper with the actual dependency versions and session attributes.
The `springSessionDefaultRedisSerializer` bean selects the serializer; changing it does not migrate stored values.
`GenericJackson2JsonRedisSerializer` is deprecated for removal in Spring Data Redis 4.0.
A custom mapper must preserve the type information needed for polymorphic values and use the appropriate Spring Security Jackson modules when security objects are stored.
Verify the actual authentication and principal types, including custom classes, rather than assuming that registering modules guarantees compatibility.

### Serialization transition

Before sharing a namespace during a rollout, test both directions with the old and new deployed versions, serializers, mapper settings, and representative attributes, including the security context.
Test Java-to-JSON separately from JSON-to-JSON version changes.
Use a real disposable Redis instance and the repository type in use to test partial field updates in the same session hash, mixed formats, expiration and rollback.
A successful serializer-only round trip does not establish repository behavior.

If either version cannot read all values the other can write, do not perform a mixed-version rollout in the same namespace.
Choose a tested migration with an upgrade and rollback plan, isolate the new format in a separate namespace with reauthentication and old-session expiry or cleanup, or explicitly invalidate existing sessions and require reauthentication.
Coordinate traffic cutover and drain or block old writers before invalidation so they cannot recreate incompatible sessions.
Plan rollback after new-format writes for each choice.
Account for traffic routing, old session expiry, and rollback after new-format writes; a namespace change alone does not transfer sessions.
For indexed repositories also verify indexes, expiration keys and lifecycle events; their expiration handling differs from the default repository.

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

`RedisSessionMapper` controls how Redis hash fields map to sessions.
Attach the required `BiFunction` to the repository through `SessionRepositoryCustomizer`:

```java
@Bean
BiFunction<String, Map<String, Object>, MapSession> redisSessionMapper() {
    return new RedisSessionMapper();
}

@Bean
SessionRepositoryCustomizer<RedisSessionRepository> redisSessionRepositoryCustomizer(
        BiFunction<String, Map<String, Object>, MapSession> mapper) {
    return repository -> repository.setRedisSessionMapper(mapper);
}
```

## Customizing the expiration store

`RedisSessionExpirationStore` manages how and when session keys expire in Redis.
Provide a custom bean to change expiration behavior:

```java
@Bean
RedisSessionExpirationStore redisSessionExpirationStore(
        RedisOperations<String, Object> redisOperations) {
    return new SortedSetRedisSessionExpirationStore(
        redisOperations,
        RedisIndexedSessionRepository.DEFAULT_NAMESPACE);
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
- Do not mix incompatible or unverified serialization strategies across applications sharing the same Redis namespace.
  - Enforce one strategy or validate both directions and rollback for a transition.
- Do not assume `SessionExpiredEvent` fires at the exact moment of TTL expiry.
  - Redis expiration is approximate and events may arrive with delay.
- Do not use the in-memory Spring Security `SessionRegistryImpl` when concurrent-session limits must work across nodes.
  - Inject `SpringSessionBackedSessionRegistry` into `maximumSessions()` instead.
