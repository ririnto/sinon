# Spring Boot service connections

Open this reference when Boot should derive test service connection properties automatically.

```java
@Bean
@ServiceConnection
PostgreSQLContainer<?> postgres() {
    return new PostgreSQLContainer<>("postgres:17");
}
```

Prefer this over hand-copying dynamic URLs when Boot can manage the service connection for you.

## Gotchas

- Do not duplicate manual `DynamicPropertySource` wiring when `@ServiceConnection` already owns the connection details.
