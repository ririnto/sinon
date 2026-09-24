# Spring Boot Testcontainers

Open this reference when tests need a real backing service through Testcontainers.

On Spring Boot 4.1.1, Boot's dependency management supplies the Testcontainers 2.x versions without explicit version declarations.
Include `spring-boot-testcontainers` as a test dependency when using `@ServiceConnection`.

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
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
```

```kotlin
dependencies {
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
}
```

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class CatalogRepositoryTests {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");
}
```

Use a real container only when a slice test is no longer enough.

Use `@ServiceConnection` instead of `@DynamicPropertySource` when Boot can derive the connection details automatically.

## Validation rule

Verify the test actually reaches the real backing service rather than an accidental in-memory fallback.
