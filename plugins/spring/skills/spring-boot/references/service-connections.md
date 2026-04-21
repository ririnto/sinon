# Spring Boot service connections

Open this reference when Boot should derive test service connection properties automatically.

## Add the Boot Testcontainers module

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
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
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
}
```

```java
@TestConfiguration(proxyBeanMethods = false)
class ContainerConfig {
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17");
    }
}

@SpringBootTest
@Import(ContainerConfig.class)
class CatalogRepositoryTests {
}
```

Prefer this over hand-copying dynamic URLs when Boot can manage the service connection for you.

## Gotchas

- Do not duplicate manual `DynamicPropertySource` wiring when `@ServiceConnection` already owns the connection details.
- When the bean returns `GenericContainer<?>` instead of a typed container, set `@ServiceConnection(name = "postgres")` or the appropriate service name explicitly.
