# Spring Session Redis advanced configuration

Open this reference when Redis session behavior needs customization beyond the ordinary path, especially repository type, session events, JSON serialization, expiration-store behavior, or Spring Security listener integration.

Keep repository type, serialization, and event behavior explicit because Redis defaults are not interchangeable with indexed lookup or cross-application payload compatibility.

## Repository type decision

- Use the default repository when the application only needs ordinary session persistence.
- Use the indexed repository when the application needs `findByPrincipalName(...)`, Spring Security concurrent-session integration, or published session events.

```yaml
spring:
  session:
    redis:
      repository-type: indexed
```

## JSON serialization shape

```java
@Bean
RedisSerializer<Object> springSessionDefaultRedisSerializer(ObjectMapper objectMapper) {
    return new GenericJackson2JsonRedisSerializer(objectMapper);
}
```

Use explicit JSON serialization when rolling upgrades, cross-service inspection, or serializer compatibility matter more than opaque default serialization.

## Session events and listener bridge

- Indexed Redis sessions can publish created, deleted, and expired session events.
- Use listener bridging only when a framework integration or audit requirement actually depends on servlet-style session events.

## Decision points

| Situation | Use |
| --- | --- |
| Spring Security needs principal-indexed lookup | indexed repository |
| Operations or audit logic need session lifecycle events | indexed repository plus event handling |
| Rolling upgrades or multiple apps share the same Redis session data | explicit JSON serializer |
| Expiration handling must be reasoned about explicitly | customize namespace, timeout, and expiration-store behavior together |

## Gotchas

- Do not inject `FindByIndexNameSessionRepository` unless the repository type actually supports principal indexing.
- Do not switch serializers without confirming compatibility for existing stored sessions.
- Do not assume expiration timing is identical across every Redis deployment or failover scenario.
