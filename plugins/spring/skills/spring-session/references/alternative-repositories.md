# Spring Session alternative repositories

Open this reference only when Redis and JDBC are both poor fits and an existing platform standard already mandates another repository.

Prefer Redis or JDBC unless the deployment explicitly requires a different store.
A custom repository changes durability and operational behavior at the core session layer.

## Repository choice

Hazelcast and MongoDB Spring Session modules do not currently have stable 4.1.0 releases in Maven metadata.
Keep Redis or JDBC as the preferred repository path unless another repository has independently verified stable support for the Spring Session line used by the application.

## Custom SessionRepository

Implement `SessionRepository<S>` or `ReactiveSessionRepository<S>` only when no current module fits.

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
