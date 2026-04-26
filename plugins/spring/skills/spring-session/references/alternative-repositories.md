# Spring Session alternative repositories

Open this reference only when Redis and JDBC are both poor fits and an existing platform standard already mandates another repository.

Prefer Redis or JDBC unless the deployment already operates another repository as a stable platform dependency.

## Common alternatives

| Repository | Good fit |
| --- | --- |
| Community extension with a published 4.0.3-compatible release | The platform already mandates that store and the exact release line has been verified before adoption |
| Custom `SessionRepository` | Regulatory or proprietary infrastructure requirements make the built-in repositories unsuitable |

## Custom repository configuration shape

Use this shape when the platform already exposes a durable session store API and the application must adapt Spring Session to it.

```java
@Configuration
@EnableSpringHttpSession
class PlatformSessionConfig {
    @Bean
    SessionRepository<MapSession> sessionRepository(PlatformSessionStore store) {
        return new PlatformSessionRepository(store, Duration.ofMinutes(30));
    }
}
```

```java
final class PlatformSessionRepository implements FindByIndexNameSessionRepository<MapSession> {
    private final PlatformSessionStore store;
    private final Duration defaultMaxInactiveInterval;

    PlatformSessionRepository(PlatformSessionStore store, Duration defaultMaxInactiveInterval) {
        this.store = store;
        this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
    }

    @Override
    public MapSession createSession() {
        MapSession session = new MapSession();
        session.setMaxInactiveInterval(defaultMaxInactiveInterval);
        return session;
    }

    @Override
    public void save(MapSession session) {
        store.save(session);
    }

    @Override
    public MapSession findById(String id) {
        return store.load(id);
    }

    @Override
    public void deleteById(String id) {
        store.delete(id);
    }

    @Override
    public Map<String, MapSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
        if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
            return Map.of();
        }
        return store.findByPrincipal(indexValue);
    }
}
```

This keeps Spring Session responsible for the HTTP integration while the platform-owned store remains responsible for persistence and principal indexing.

## Store-backed test shape

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PlatformSessionFlowTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void customRepositoryReusesTheSameSessionAcrossRequests() throws Exception {
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
- Do not assume older Hazelcast or MongoDB session modules are available on the current 4.0.3 line without checking their published artifacts first.
- Do not skip store-backed tests for session creation, reuse, expiration, and principal lookup.
- Do not treat a custom repository as a small task; it changes durability and operational behavior at the core session layer.
