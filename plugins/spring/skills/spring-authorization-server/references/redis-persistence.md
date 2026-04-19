# Spring Authorization Server Redis persistence

Open this reference when the task requires `RegisteredClientRepository`, `OAuth2AuthorizationService`, or `OAuth2AuthorizationConsentService` backed by Redis.

## When to open this reference

Open this reference when:

- Authorization state must survive restarts in a Redis-backed session or cache topology
- Token issuance latency targets favor Redis over relational persistence
- The platform already operates Redis as the primary state store
- Horizontal scaling requires shared state without sticky sessions

## Official posture

Spring Authorization Server provides an official how-to guide for implementing equivalent core services with Redis. Treat the Redis path here as an application-owned implementation pattern based on that guide, not as a built-in framework module.

### RegisteredClientRepository Redis pattern

Use an application-owned Redis-backed `RegisteredClientRepository` when registered clients must survive restarts and load from Redis:

```java
@Configuration
class RedisPersistenceConfig {
    @Bean
    RegisteredClientRepository registeredClientRepository(RedisTemplate<String, RegisteredClient> redisTemplate) {
        return customRedisRegisteredClientRepository(redisTemplate);
    }
}
```

The `RegisteredClient` is serialized as a JSON blob under the key prefix `registered_client:`.

### OAuth2AuthorizationService Redis pattern

Use an application-owned Redis-backed `OAuth2AuthorizationService` when authorization state must survive application restarts and share across cluster nodes:

```java
@Configuration
class RedisPersistenceConfig {
    @Bean
    OAuth2AuthorizationService authorizationService(RedisTemplate<String, OAuth2Authorization> redisTemplate, RegisteredClientRepository registeredClientRepository) {
        return customRedisAuthorizationService(redisTemplate, registeredClientRepository);
    }
}
```

Authorization objects are stored under the key prefix `authorization:` and serialized as JSON.

### OAuth2AuthorizationConsentService Redis pattern

Use an application-owned Redis-backed `OAuth2AuthorizationConsentService` when consent decisions must survive restarts and share across nodes:

```java
@Configuration
class RedisPersistenceConfig {
    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(RedisTemplate<String, OAuth2AuthorizationConsent> redisTemplate) {
        return customRedisAuthorizationConsentService(redisTemplate);
    }
}
```

Consent objects are stored under the key prefix `authorization_consent:`.

## Key structure

Redis implementations use these key patterns:

- `registered_client:{clientId}` stores each registered client
- `authorization:{authorizationId}` stores each authorization record
- `authorization_consent:{clientId}:{principalName}` stores each consent decision

## Serialization

Configure `RedisTemplate` with JSON serialization for the authorization server types:

```java
@Configuration
class RedisTemplateConfig {
    @Bean
    RedisTemplate<String, OAuth2Authorization> authorizationRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, OAuth2Authorization> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

## Decision points

| Situation | Choice |
| --- | --- |
| client data must survive restarts and share across nodes | custom Redis-backed `RegisteredClientRepository` |
| authorization state must survive restarts | custom Redis-backed `OAuth2AuthorizationService` |
| consent decisions must survive restarts | custom Redis-backed `OAuth2AuthorizationConsentService` |
| already operating Redis as primary state store | Redis over JPA |
| latency targets require sub-millisecond persistence | Redis over JPA |
| team prefers key-value operational model | Redis over JPA |

## Redis implementation branches

### Tuning key expiration

Authorization records grow as tokens are issued. Set explicit TTL policies:

```java
@Configuration
class RedisPersistenceConfig {
    @Bean
    OAuth2AuthorizationService authorizationService(RedisTemplate<String, OAuth2Authorization> redisTemplate, RegisteredClientRepository registeredClientRepository) {
        return customRedisAuthorizationService(redisTemplate, registeredClientRepository);
    }
}
```

TTL values should match the token lifetimes configured in `TokenSettings`, but the exact expiration strategy is part of the application-owned Redis implementation.

### Using Redis for multitenancy

When combining Redis persistence with multitenancy, scope keys by tenant:

```java
@Component
class TenantRedisTemplateFactory {
    private final TenantContext tenantContext;

    String keyFor(String prefix, String identifier) {
        return prefix + ":" + tenantContext.getCurrentTenant() + ":" + identifier;
    }
}
```

Tenant-scoped key management prevents cross-tenant authorization leakage.

### Mixing Redis and JPA

Do not mix separate Redis-backed and JPA-backed authorization services in the same application. Choose one persistence mechanism for authorizations.

## Production considerations

- Use a Redis cluster or Sentinel for high availability
- Configure appropriate key eviction policies that respect token lifetimes
- Monitor memory usage as authorization records accumulate
- Plan for key serialization changes when upgrading
- Ensure Redis connection pooling matches application concurrency

## Official documentation

See:

- [How-to: Implement core services with Redis](https://docs.spring.io/spring-authorization-server/reference/guides/how-to-redis.html)
- [RegisteredClientRepository](https://docs.spring.io/spring-authorization-server/reference/authorization-server-components.html#registeredclientrepository)
