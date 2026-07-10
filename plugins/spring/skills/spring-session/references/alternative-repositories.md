# Spring Session alternative repositories

Open this reference only when Redis and JDBC are both poor fits and an existing platform standard already mandates another repository.

Prefer Redis or JDBC unless the deployment explicitly requires a different store.
A custom repository changes durability and operational behavior at the core session layer.

## Repository choice

The [Spring Session 4.0 release notes](https://docs.spring.io/spring-session/reference/whats-new.html) record that the Hazelcast and MongoDB modules moved to the Hazelcast and MongoDB teams. Treat these integrations as independently maintained rather than assuming that Spring Session releases them in lockstep.

When a repository is required to use MongoDB or Hazelcast, use the maintained integration from its owning team and verify that its artifact is available and compatible with the application's Spring Session and Spring Boot line. For example, Maven Central publishes MongoDB's [`org.mongodb:mongodb-spring-session:4.0.0`](https://central.sonatype.com/artifact/org.mongodb/mongodb-spring-session/4.0.0). Do not infer a Hazelcast artifact or version without checking the Hazelcast maintainer's current documentation.

Keep Redis or JDBC as the preferred repository path when no platform requirement mandates another store.

## Custom SessionRepository

Implement `SessionRepository<S>` or `ReactiveSessionRepository<S>` only when no compatible maintained integration satisfies the repository requirements.

```java
class InMemorySessionRepository implements SessionRepository<Session>, FindByIndexNameSessionRepository<Session> {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private Duration defaultMaxInactiveInterval = Duration.ofMinutes(30);
    public Session createSession() {
        MapSession session = new MapSession();
        session.setMaxInactiveInterval(defaultMaxInactiveInterval);
        return session;
    }
    public void save(Session session) {
        sessions.put(session.getId(), session);
    }
    public Session findById(String id) {
        Session session = sessions.get(id);
        if (session != null && session.isExpired()) {
            deleteById(id);
            return null;
        }
        return session;
    }
    public void deleteById(String id) {
        sessions.remove(id);
    }
    public Map<String, Session> findByPrincipalName(String principalName) {
        return sessions.values().stream().filter(s -> principalName.equals(s.getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))).collect(Collectors.toMap(Session::getId, Function.identity()));
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
