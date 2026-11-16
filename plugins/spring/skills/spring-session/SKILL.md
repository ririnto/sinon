---
name: spring-session
description: >-
  Use Spring Session for session stores, ID transport, expiration, and web integration.
---

# Spring Session

Sources for this skill and its references are in the [source ledger](references/sources.md).

## Boundaries

Use `spring-session` for session storage, sharing, ID transport, repository choice, principal lookup, and `HttpSession` or `WebSession` integration.
Authentication, authorization, CSRF, login flow, endpoint design, and general Redis modeling remain outside this skill's scope.

## Version alignment

For Session and Boot compatibility and runtime minimums, read [version alignment](references/version-alignment.md).
Boot manages Session versions. Session requires Java 17+, Framework 6+ when used, Servlet 3.1+ in servlet containers, and Redis 2.8+ for `@EnableRedisHttpSession`. Do not confuse Boot platform baselines with library minimums.

## Branch selector

Use the servlet path for Redis sessions, timeout, namespace, transport, repository choice, serialization, principal lookup, and store-backed tests.
Read references when their conditions apply:

- [JDBC store](references/jdbc-store.md): relational session tables, cleanup, transactions, or JSON attributes.
- [WebFlux](references/webflux-websession.md): reactive `WebSession`.
- [WebSocket](references/websocket-integration.md): share HTTP session lifecycle with WebSocket or STOMP.
- [Redis advanced](references/redis-advanced.md): customize repository type, events, JSON serialization, expiration, mapper, or Spring Security listeners.
- [Alternative repositories](references/alternative-repositories.md): Redis and JDBC are poor fits and the deployment mandates another repository.

## Dependency baseline

Before recommending a version, check each selected Maven artifact on Maven Central for the latest stable release compatible with the project's Boot and Framework lines.
Keep project BOM or version-catalog management and pins unless the task authorizes changes.

Use the Spring Boot starter that matches the store so the session module, data-access integration, and client dependencies remain aligned.
Start with Redis for the common clustered browser-session path unless the deployment already standardizes on a different store.

For Redis or JDBC servlet starter declarations, read [store dependencies](references/store-dependencies.md).

## Store selection

- Choose Redis when multiple application nodes need low-latency shared sessions and principal-indexed lookup is useful.
- Choose JDBC when the deployment already standardizes on a relational database and the session table lifecycle is acceptable.
- Choose header-based session ids only for API or non-browser clients.
  - Use cookies for browser applications unless an existing API contract forbids them.

### Redis repository choice

- Use the default Redis repository (`RedisSessionRepository`) when the application only needs normal read, write, and expiration behavior for each session.
- Use the indexed Redis repository (`RedisIndexedSessionRepository`) when Spring Security, administration, or messaging needs `findByPrincipalName(...)`, `SessionCreatedEvent`, `SessionDeletedEvent`, `SessionDestroyedEvent`, or `SessionExpiredEvent`.
- Keep timeout, namespace, and expiration explicit so operators can reason about TTL and cleanup.

## Session configuration

For Redis properties, cookie customization, header transport, or serialization, read [session configuration](references/session-configuration.md) and the [configuration examples](references/configuration-examples.md).
For principal lookup, use indexed Redis; the starter and `repository-type: indexed` select it without an enable annotation.
Use header transport only for non-browser contracts.
Use the default Redis serializer for a single application controlling reads and writes. Consider JSON when sharing, upgrades, or inspection make it too brittle, and verify the selected mapper and attributes.
For every live format change, follow the [transition contract](references/redis-advanced.md#serialization-transition). Do not share a namespace during a mixed rollout until both directions and rollback are validated.

## Coding procedure

1. Identify whether the application is servlet-based (`HttpSession`) or reactive (`WebSession`).
   - Do not mix the two models in examples or configuration.
2. Choose the backing store before touching security or controller code.
   - Session infrastructure should be stable first.
3. Set `spring.session.timeout` and the session namespace or table name explicitly so runtime behavior is obvious in operations.
4. For Redis, choose the repository type explicitly: default for ordinary storage, indexed when principal lookup or session events are required.
5. Decide how clients carry the session id: cookie for browsers, header for API clients, or a custom resolver only when an existing contract requires it.
6. Add serializer and cookie policy customization where cross-subdomain login, `SameSite`, JSON payload compatibility, or rolling upgrades matter.
7. Use the matching reference when the task affects Spring Security, WebSocket, or session administration integration.
8. Use store-backed tests when the changed contract depends on reuse, expiration, serialization, or indexed lookup.

## Session events

Session events and `HttpSessionListener` bridging are additive, not the ordinary path.
For security concurrency, audit, or administrator invalidation using indexed lookup or events, read [session events](references/session-events.md) and [Redis advanced](references/redis-advanced.md).
Use `SpringSessionBackedSessionRegistry` with indexed Redis for concurrent-session limits across nodes.

## Implementation examples

For Spring Security principal lookup, invalidation, or integration tests, read [implementation examples](references/implementation-examples.md).

## Testing checklist

Select checks for affected contracts and regression risks, including session or access protections where applicable.

- Verify the second request reuses the same session id and sees state written by the first request.
- Verify expiration behavior with the configured timeout instead of assuming the store default.
- For Redis, verify the configured namespace, repository type, and serialization strategy match the deployed cluster conventions.
- For Spring Security integration, verify logout invalidates the stored session and Spring Security's concurrent-session control uses the same Spring Session repository when indexed lookup is required.
- For header-based session ids, verify the client sends and rotates the expected header.

## Production checklist

- Keep session payloads small and serialization-safe.
  - Store identifiers and lightweight state, not large object graphs.
- Align cookie domain, `SameSite`, and secure flags with the actual deployment topology before release.
- For Redis, confirm TTL behavior, namespace isolation, repository type, serializer compatibility, and failover latency under node loss.
- For Redis Cluster with the indexed repository, note that events are subscribed from a single random node.
  Some session indexes may not be cleaned up if the event occurs on a different node.
- Expose login, active session, and session expiration behavior through application metrics or audit events when operations need visibility.
- Ensure secret rotation or node restarts do not silently invalidate every active session unless that behavior is intentional.
