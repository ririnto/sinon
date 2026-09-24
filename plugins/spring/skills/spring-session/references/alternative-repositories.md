# Spring Session alternative repositories

Open this reference only when Redis and JDBC are both poor fits and an existing platform standard already mandates another repository.

Prefer Redis or JDBC unless the deployment explicitly requires a different store.
A custom repository changes durability and operational behavior at the core session layer.

## Repository choice

Spring Session 4.0 moved the Hazelcast and MongoDB modules to the Hazelcast and MongoDB teams.
Treat these integrations as independently maintained rather than assuming that Spring Session releases them in lockstep.

When a repository is required to use MongoDB or Hazelcast, use the maintained integration from its owning team and verify that its artifact is available and compatible with the application's Spring Session and Spring Boot line.
For MongoDB, check `org.mongodb:mongodb-spring-session` on Maven Central for the latest stable release compatible with the project's Spring Session and Boot lines.
Keep an existing BOM or version-catalog pin unless the task authorizes changing it.
Do not infer a Hazelcast artifact or version without checking the Hazelcast maintainer's current documentation.

Keep Redis or JDBC as the preferred repository path when no platform requirement mandates another store.

## Custom SessionRepository

Implement `SessionRepository<S>` or `ReactiveSessionRepository<S>` only when no compatible maintained integration satisfies the repository requirements.

```java
class InMemorySessionRepository implements SessionRepository<Session>, FindByIndexNameSessionRepository<Session> {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private Duration defaultMaxInactiveInterval = Duration.ofMinutes(30);
    /**
     * Creates a new session with the repository's default inactive interval.
     */
    public Session createSession() {
        MapSession session = new MapSession();
        session.setMaxInactiveInterval(defaultMaxInactiveInterval);
        return session;
    }
    /**
     * Stores the session by id in the in-memory map.
     */
    public void save(Session session) {
        sessions.put(session.getId(), session);
    }
    /**
     * Returns the stored session, deleting it instead when it has expired.
     */
    public Session findById(String id) {
        Session session = sessions.get(id);
        if (session == null || !session.isExpired()) {
            return session;
        }
        deleteById(id);
        return null;
    }
    /**
     * Removes the session with the given id.
     */
    public void deleteById(String id) {
        sessions.remove(id);
    }
    /**
     * Maps sessions stored under the principal-name index attribute to their ids.
     */
    public Map<String, Session> findByPrincipalName(String principalName) {
        return sessions.values().stream().filter(s -> principalName.equals(s.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))).collect(Collectors.toMap(Session::getId, Function.identity()));
    }
    void setDefaultMaxInactiveInterval(Duration interval) {
        this.defaultMaxInactiveInterval = interval;
    }
}
```

Register the repository, enable Spring Session's filter infrastructure, and register that filter with the servlet container:

```java
@Configuration
@EnableSpringHttpSession
class CustomSessionConfig {
    @Bean
    InMemorySessionRepository sessionRepository() {
        return new InMemorySessionRepository();
    }
}

/**
 * Registers the Spring Session filter with the servlet container.
 */
public class Initializer extends AbstractHttpSessionApplicationInitializer {
    /**
     * Wires the initializer to the custom session configuration.
     */
    public Initializer() {
        super(CustomSessionConfig.class);
    }
}
```

`@EnableWebSecurity` configures Spring Security, not the Spring Session repository filter, and is not a substitute for this registration.
