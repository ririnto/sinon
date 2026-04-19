# Spring Boot Testcontainers

Open this reference when tests need a real backing service through Testcontainers.

```java
@Bean
PostgreSQLContainer<?> postgres() {
    return new PostgreSQLContainer<>("postgres:17");
}
```

Use a real container only when a slice test is no longer enough.

## Validation rule

Verify the test actually reaches the real backing service rather than an accidental in-memory fallback.
