# Spring Session alternative repositories

Open reference only when Redis and JDBC are both poor fits and an existing platform standard already mandates another repository.

Prefer Redis or JDBC unless the deployment explicitly requires a different store. A custom repository changes durability and operational behavior at the core session layer.

## Community-led modules

Starting with Spring Session 4.0, Hazelcast and MongoDB session modules are no longer maintained in the main Spring Session repository. They are now led by their respective communities:

- Hazelcast: maintained by the Hazelcast team
- MongoDB: maintained by the MongoDB team

Verify that the community module publishes a 4.1.0-compatible release before adopting it.

## Custom SessionRepository

Implement `SessionRepository<S>` or `ReactiveSessionRepository<S>` when no existing module fits:

```java
class InMemorySessionRepository implements SessionRepository<Session>, FindByIndexNameSessionRepository<Session> {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private Duration defaultMaxInactiveInterval = Duration.ofMinutes(30);

    @Override
    public Session createSession() {
        MapSession session = new MapSession();
        session.setMaxInactiveInterval(defaultMaxInactiveInterval);
        return session;
    }

    @Override
    public void save(Session session) {
        sessions.put(session.getId(), session);
    }

    @Override
    public Session findById(String id) {
        Session session = sessions.get(id);
        if (session == null) {
            return null;
        }
        if (session.isExpired()) {
            deleteById(id);
            return null;
        }
        return session;
    }

    @Override
    public void deleteById(String id) {
        sessions.remove(id);
    }

    @Override
    public Map<String, Session> findByPrincipalName(String principalName) {
        return sessions.values().stream()
            .filter(s -> principalName.equals(
                s.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME)))
            .collect(Collectors.toMap(Session::getId, Function.identity()));
    }

    void setDefaultMaxInactiveInterval(Duration interval) {
        this.defaultMaxInactiveInterval = interval;
    }
}
```

Register the repository and filter:

```java
@Configuration
@EnableWebSecurity
class CustomSessionConfig {
    @Bean
    InMemorySessionRepository sessionRepository() {
        return new InMemorySessionRepository();
    }

    @Bean
    FindByIndexNameSessionRepository<? extends Session> indexedSessionRepository() {
        return sessionRepository();
    }
}
```

### Reactive custom repository

```java
class ReactiveInMemorySessionRepository implements ReactiveSessionRepository<Session> {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Mono<Session> createSession() {
        return Mono.just(new MapSession());
    }

    @Override
    public Mono<Void> save(Session session) {
        sessions.put(session.getId(), session);
        return Mono.empty();
    }

    @Override
    public Mono<Session> findById(String id) {
        return Mono.justOrEmpty(sessions.get(id));
    }

    @Override
    public Mono<Void> deleteById(String id) {
        sessions.remove(id);
        return Mono.empty();
    }
}
```

## Example integration test with custom repository

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CustomRepositorySessionFlowTest {
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

## Gotchas

- Do not pick an alternative repository just to avoid learning Redis or JDBC defaults.
- Do not assume older Hazelcast or MongoDB session modules are available on the current 4.1.0 line without checking their published artifacts first.
- Do not skip store-backed tests for session creation, reuse, expiration, and principal lookup.
- Do not treat a custom repository as a small task; it changes durability and operational behavior at the core session layer.
