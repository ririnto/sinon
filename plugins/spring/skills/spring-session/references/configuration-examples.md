# Configuration and test examples

## Example 1: Redis-backed application properties

```yaml
spring:
  session:
    timeout: 30m
    data:
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

## Example 2: Cookie customization and indexed repository shape

```java
@Configuration
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

## Example 3: Clustered Spring Security concurrency

```java
@Bean
SpringSessionBackedSessionRegistry<RedisIndexedSessionRepository.RedisSession> sessionRegistry(RedisIndexedSessionRepository sessions) {
    return new SpringSessionBackedSessionRegistry<>(sessions);
}

@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http, SpringSessionBackedSessionRegistry<RedisIndexedSessionRepository.RedisSession> sessionRegistry) throws Exception {
    return http
        .sessionManagement(session -> session.maximumSessions(2).sessionRegistry(sessionRegistry))
        .build();
}
```
