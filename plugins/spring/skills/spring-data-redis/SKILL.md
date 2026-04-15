---
name: spring-data-redis
description: >-
  Use this skill when the user asks to "use Spring Data Redis", "model Redis data in Spring", "set TTL in Redis", "use Redis repositories or templates", or needs guidance on Spring Data Redis patterns.
---

# Spring Data Redis

## Overview

Use this skill to model Redis-backed data, templates, repositories, key-space usage, TTL, and messaging patterns in Spring. The common case is deciding whether the use case is aggregate-like key/value state, lower-level Redis operations, or pub/sub, then choosing repositories or templates accordingly. Keep key strategy, serialization, and expiration explicit.

## Use This Skill When

- You are modeling Redis-backed state in Spring.
- You need to choose repositories versus `RedisTemplate` or `StringRedisTemplate`.
- You need explicit TTL, key-space, or pub/sub behavior.
- Do not use this skill when the problem is relational persistence or generic caching theory without Spring Data Redis specifics.

## Common-Case Workflow

1. Identify whether the data is cache-like, key-value state, stream state, or messaging.
2. Use repositories for aggregate-style key-space work and templates when lower-level Redis operations are needed.
3. Make TTL and key-space behavior explicit.
4. Keep serialization and messaging decisions visible.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one Redis aggregate and one repository:

```java
@RedisHash("session")
class SessionEntry {
    @Id
    String id;
    String userId;
    @TimeToLive
    Long ttlSeconds;
}

interface SessionRepository extends CrudRepository<SessionEntry, String> {
}
```

---

*Applies when:* the use case is aggregate-like Redis state rather than lower-level command work.

## Ready-to-Adapt Templates

TTL-backed aggregate:

```java
@RedisHash("session")
class SessionEntry {
    @Id
    String id;
    String userId;
    @TimeToLive
    Long ttlSeconds;
}
```

---

*Applies when:* the data has a meaningful lifetime and should expire explicitly.

Repository:

```java
interface SessionRepository extends CrudRepository<SessionEntry, String> {
}
```

---

*Applies when:* the use case is key-value aggregate persistence rather than custom command sequences.

Pub/sub publisher:

```java
@Service
class SessionPublisher {
    private final StringRedisTemplate redisTemplate;

    SessionPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    void publish(String message) {
        redisTemplate.convertAndSend("sessions.events", message);
    }
}
```

---

*Applies when:* the use case is channel-based messaging rather than stored aggregate state.

## Validate the Result

Validate the common case with these checks:

- key-space and expiration behavior are explicit
- repositories and templates are chosen according to operation shape
- serialization choices are visible and intentional
- unrelated data shapes do not share one vague key pattern

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| repository-vs-template choice, TTL details, or key strategy | `./references/redis-patterns.md` |

## Invariants

- MUST keep key-space and expiration behavior explicit.
- SHOULD choose repositories vs templates based on operation shape.
- MUST keep serialization decisions visible.
- SHOULD model Redis data around actual access patterns.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| treating Redis like a relational store with no key strategy | key access and evolution become incoherent | design explicit prefixes and access shapes |
| hiding TTL decisions | data lifetime becomes surprising in production | make expiration explicit in the model or command path |
| mixing repository-style and low-level template use without a clear reason | the access model gets muddy | pick the abstraction that matches the operation shape |

## Scope Boundaries

- Activate this skill for:
  - Spring Data Redis modeling
  - repositories and templates
  - expiration and pub/sub behavior
- Do not use this skill as the primary source for:
  - relational persistence
  - generic caching theory without Spring Data Redis specifics
