---
name: "spring-session"
description: "Replace container-local sessions with Spring Session across Spring Security, WebFlux, and WebSocket endpoints using a chosen backing store and customized cookies or headers. Use this skill when replacing container-local sessions with Spring Session, choosing a backing session store, customizing session cookies or headers, and integrating shared sessions with Spring Security, WebFlux, or WebSocket endpoints."
metadata:
  title: "Spring Session"
  official_project_url: "https://spring.io/projects/spring-session"
  reference_doc_urls:
    - "https://docs.spring.io/spring-session/reference/index.html"
  version: "4.0.2"
---

Use this skill when replacing container-local sessions with Spring-managed shared sessions, choosing a backing store, customizing session id transport, or wiring shared sessions into Spring Security and related web behavior.

## Boundaries

Use `spring-session` for session persistence, clustered session sharing, cookie or header-based session id transport, repository choice, principal-indexed session lookup, and framework integration around `HttpSession` or `WebSession`.

- Keep authentication, authorization, CSRF policy, and login flow design outside this skill's scope. Keep `spring-session` focused on where session state lives and how it is resolved.
- Keep controller, handler, and endpoint design outside this skill's scope. Keep this skill focused on session infrastructure and transport.
- Keep general Redis data modeling and broad `RedisTemplate` usage outside this skill's scope when the task is not primarily about session storage.

## Common path

The ordinary Spring Session job is:

1. Choose the runtime model first: servlet `HttpSession` or reactive `WebSession`.
2. Choose the session store and timeout policy.
3. Add the smallest matching dependency set.
4. Configure session persistence, repository behavior, and session id transport.
5. Integrate indexed lookup with security or messaging only where needed.
6. Add a focused integration test that proves session creation, reuse, expiration, and principal lookup.

### Branch selector

- Stay in `SKILL.md` for the ordinary servlet path: Redis-backed servlet sessions, explicit timeout and namespace, cookie or header transport choice, indexed-versus-default Redis repository choice, cookie customization, JSON serializer awareness, principal lookup, and store-backed tests.
- Open [references/jdbc-store.md](references/jdbc-store.md) when the session store is relational and table naming, cleanup, transactions, or JSON attribute storage matter.
- Open [references/webflux-websession.md](references/webflux-websession.md) when the application is reactive and uses `WebSession` instead of servlet `HttpSession`.
- Open [references/websocket-integration.md](references/websocket-integration.md) when WebSocket or STOMP traffic must share the same backing session lifecycle as HTTP traffic.
- Open [references/redis-advanced.md](references/redis-advanced.md) when Redis repository type, session events, JSON serialization, expiration-store behavior, or Spring Security listener integration must be customized.
- Open [references/alternative-repositories.md](references/alternative-repositories.md) only when Redis and JDBC are both poor fits and the deployment already mandates another repository.

## Dependency baseline

Prefer the Spring Session module that matches the store, plus the matching data-access starter or driver support. Start with Redis for the common clustered browser-session path unless the deployment already standardizes on a different store.

### Redis-backed servlet sessions

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.session</groupId>
        <artifactId>spring-session-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
</dependencies>
```

## Store selection

- Choose Redis when multiple application nodes need low-latency shared sessions and principal-indexed lookup is useful.
- Choose JDBC when the deployment already standardizes on a relational database and the session table lifecycle is acceptable.
- Choose header-based session ids only for API or non-browser clients. Use cookies for browser applications unless an existing API contract forbids them.

### Redis repository choice

- Use the default Redis repository when the application only needs normal read, write, and expiration behavior for each session.
- Use the indexed Redis repository when Spring Security, administration, or messaging needs `findByPrincipalName(...)` or session lifecycle events.
- Keep timeout, namespace, and expiration behavior explicit so operators can reason about TTL and cleanup behavior.

## First safe configuration

### Redis-backed application properties

```yaml
spring:
  session:
    store-type: redis
    timeout: 30m
    redis:
      namespace: app:session
      repository-type: indexed
      flush-mode: on-save
      save-mode: on-set-attribute
  data:
    redis:
      host: localhost
      port: 6379
server:
  servlet:
    session:
      cookie:
        name: SESSION
        http-only: true
        secure: true
```

### Cookie customization and indexed repository shape

Use the indexed Redis variant when principal lookup is part of the job.

```java
@Configuration
@EnableRedisIndexedHttpSession
class SessionConfig {
    @Bean
    CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookiePath("/");
        return serializer;
    }
}
```

### Header-based session id shape

Use this only when a non-browser client contract requires header transport.

```java
@Bean
HttpSessionIdResolver httpSessionIdResolver() {
    return HeaderHttpSessionIdResolver.xAuthToken();
}
```

### Serialization rule

The default Redis serializer is acceptable for one application controlling both reads and writes. Switch to JSON serialization when multiple applications, rolling upgrades, or payload inspection requirements make default serialization too brittle.

## Coding procedure

1. Identify whether the application is servlet-based (`HttpSession`) or reactive (`WebSession`). Do not mix the two models in examples or configuration.
2. Choose the backing store before touching security or controller code. Session infrastructure should be stable first.
3. Set `spring.session.timeout` and the session namespace or table name explicitly so runtime behavior is obvious in operations.
4. For Redis, choose the repository type explicitly: default for ordinary storage, indexed when principal lookup or session events are required.
5. Decide how clients carry the session id: cookie for browsers, header for API clients, or a custom resolver only when an existing contract requires it.
6. Add serializer and cookie policy customization where cross-subdomain login, `SameSite`, JSON payload compatibility, or rolling upgrades matter.
7. Integrate with Spring Security, WebSocket, or session administration only after store-backed session creation and reuse work in isolation.
8. Add a store-backed integration test that proves session reuse across requests and verifies expiration or indexed lookup.

## Session events and listener boundary

- Session lifecycle events and `HttpSessionListener` bridging are additive features, not the ordinary path.
- Open the Redis advanced reference when Spring Security concurrent-session control, audit handling, or administrator-driven invalidation depends on indexed lookup or published session events.

## Edge cases

- Open [references/jdbc-store.md](references/jdbc-store.md) when the database schema, cleanup cadence, transaction strategy, or JSON attribute storage needs explicit control.
- Open [references/webflux-websession.md](references/webflux-websession.md) when the app is reactive and `WebSession` semantics replace servlet `HttpSession` semantics.
- Open [references/websocket-integration.md](references/websocket-integration.md) when WebSocket or STOMP traffic must close or invalidate alongside the backing HTTP session.
- Open [references/redis-advanced.md](references/redis-advanced.md) when repository type, session events, listener bridging, expiration-store behavior, or JSON serialization must be customized.
- Open [references/alternative-repositories.md](references/alternative-repositories.md) only when an existing platform standard already mandates Hazelcast, MongoDB, or a custom repository.

## Implementation examples

### Spring Security principal lookup and session invalidation

```java
@Service
class SessionAdministrationService {
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    SessionAdministrationService(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    void expireAllSessionsFor(String username) {
        Map<String, ? extends Session> sessions =
            sessionRepository.findByPrincipalName(username);

        for (String sessionId : sessions.keySet()) {
            sessionRepository.deleteById(sessionId);
        }
    }
}
```

### Example integration test shape

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SessionFlowTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void sessionStateIsReusedAcrossRequests() throws Exception {
        MvcResult first = mockMvc.perform(post("/cart/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-1\"}"))
            .andExpect(status().isOk())
            .andReturn();

        Cookie sessionCookie = first.getResponse().getCookie("SESSION");

        ResultActions secondRequest = mockMvc.perform(post("/cart/items")
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-2\"}"));
        assertAll(
            () -> secondRequest.andExpect(status().isOk()),
            () -> secondRequest.andExpect(jsonPath("$.itemCount").value(2))
        );
    }
}
```

## Output contract

Return:

1. The chosen runtime model, session store, and transport strategy
2. The main configuration shape, including timeout, namespace or table name, and cookie or header policy
3. The chosen Redis repository variant when Redis is selected
4. The integration point with security, messaging, or administration, if one is required
5. The test shape proving session creation, reuse, expiration, and any indexed lookup requirement
6. Any blocker that requires the JDBC, WebFlux, WebSocket, Redis advanced, or alternative-repository references

## Testing checklist

- Verify the second request reuses the same session id and sees state written by the first request.
- Verify expiration behavior with the configured timeout instead of assuming the store default.
- For Redis, verify the configured namespace, repository type, and serialization strategy match the deployed cluster conventions.
- For Spring Security integration, verify logout invalidates the stored session and concurrent-session controls see the same repository when indexed lookup is required.
- For header-based session ids, verify the client sends and rotates the expected header.

## Production checklist

- Keep session payloads small and serialization-safe. Store identifiers and lightweight state, not large object graphs.
- Align cookie domain, `SameSite`, and secure flags with the actual deployment topology before release.
- For Redis, confirm TTL behavior, namespace isolation, repository type, serializer compatibility, and failover latency under node loss.
- Expose login, active session, and session expiration behavior through application metrics or audit events when operations need visibility.
- Ensure secret rotation or node restarts do not silently invalidate every active session unless that behavior is intentional.

## References

- Open [references/jdbc-store.md](references/jdbc-store.md) when the store is relational.
- Open [references/webflux-websession.md](references/webflux-websession.md) when the runtime is reactive.
- Open [references/websocket-integration.md](references/websocket-integration.md) when messaging and session lifecycle must stay aligned.
- Open [references/redis-advanced.md](references/redis-advanced.md) when Redis session behavior needs customization beyond the ordinary path.
- Open [references/alternative-repositories.md](references/alternative-repositories.md) when the platform mandates a non-Redis, non-JDBC repository.
