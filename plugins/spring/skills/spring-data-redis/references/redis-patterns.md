---
title: Spring Data Redis Patterns Reference
description: >-
  Reference for Spring Data Redis repository, TTL, key strategy, and usage patterns.
---

Use this reference when the Redis use case is known and the remaining work is choosing repositories, templates, TTL, and key strategy.

## Repository vs Template Rule

- repositories for aggregate-like key-space operations
- templates for lower-level Redis commands, pub/sub, or custom key access

### Repository shape

```java
@RedisHash("sessions")
public class UserSession {
    @Id private String id;
    private String userId;
    private Instant lastSeen;
}

public interface UserSessionRepository extends CrudRepository<UserSession, String> {
    List<UserSession> findByUserId(String userId);
}
```

### Template shape

```java
@Autowired
private RedisTemplate<String, Object> template;

public void publishEvent(String channel, Object payload) {
    template.convertAndSend(channel, payload);
}

public boolean claimLock(String lockKey, Duration ttl) {
    return Boolean.TRUE.equals(
        template.opsForValue().setIfAbsent(lockKey, Thread.currentThread().getName(), ttl));
}
```

Use repository for domain-model operations; use template for pub/sub, ad hoc key access, and operations that do not map naturally to a repository.

## WATCH / SessionCallback Optimistic Locking Example

`SessionCallback` wraps multiple commands in a single `WATCH`-`MULTI`-`EXEC` transaction:

```java
@Autowired
private RedisTemplate<String, Object> template;

public boolean reserveItem(String inventoryKey, int qty) {
    return template.execute(new SessionCallback<Boolean>() {
        @Override
        @SuppressWarnings("unchecked")
        public Boolean execute(RedisOperations operations) throws DataAccessException {
            operations.watch(inventoryKey);
            Integer current = (Integer) operations.opsForValue().get(inventoryKey);
            if (current == null || current < qty) {
                operations.unwatch();
                return false;
            }
            operations.multi();
            operations.opsForValue().set(inventoryKey, current - qty);
            return "QUEUED".equals(operations.exec().get(0).toString());
        }
    });
}
```

If another client modifies `inventoryKey` between `WATCH` and `EXEC`, the transaction aborts and `exec()` returns `null`; retry or fail depending on business needs.

## Lua Script Example

Use Lua when one atomic server-side operation replaces several client round-trips:

```java
@Configuration
class RedisConfig {

    @Bean
    DefaultRedisScript<Long> decrementScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
            "local current = redis.call('GET', KEYS[1]) " +
            "if current == false then return 0 end " +
            "local next = tonumber(current) - tonumber(ARGV[1]) " +
            "if next < 0 then return -1 end " +
            "redis.call('SET', KEYS[1], next) " +
            "return next"
        );
        script.setResultType(Long.class);
        return script;
    }
}
```

```java
@Autowired
private RedisTemplate<String, Object> template;

@Autowired
private DefaultRedisScript<Long> decrementScript;

public long tryDecrement(String inventoryKey, int qty) {
    Long result = template.execute(
        decrementScript,
        List.of(inventoryKey),
        String.valueOf(qty)
    );
    return result != null ? result : -1;  // -1 means insufficient stock
}
```

Lua scripts execute atomically on the Redis server; no client-side retry or WATCH needed for single-key operations.

## Key Strategy Checklist

- use a predictable prefix per data shape
- do not mix unrelated concepts under one key pattern
- make serialization choice visible in configuration

## Concurrency and Atomicity Rule

Use Redis features intentionally when multiple clients may update the same keys.

- `WATCH` and `SessionCallback` for optimistic multi-key coordination
- Lua scripts when one atomic server-side operation is clearer than several client round-trips

## Operational Note

Keep connection and topology behavior visible.

- cluster and sentinel deployments need explicit client refresh and failover expectations
- pool settings should match real concurrency and idle-connection behavior instead of staying implicit

## Common Mistakes

- hiding TTL behavior
- mixing unrelated data shapes into one Redis model
- implicit serialization surprises
- assuming Redis updates are automatically safe under concurrent writers without a deliberate atomicity strategy
