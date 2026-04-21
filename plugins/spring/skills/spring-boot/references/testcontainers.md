# Spring Boot Testcontainers

Open this reference when tests need a real backing service through Testcontainers.

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

```kotlin
dependencies {
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}
```

```java
@SpringBootTest
@Testcontainers
class CatalogRepositoryTests {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

Use a real container only when a slice test is no longer enough.

Use `@ServiceConnection` instead of `@DynamicPropertySource` when Boot can derive the connection details automatically.

## Validation rule

Verify the test actually reaches the real backing service rather than an accidental in-memory fallback.
