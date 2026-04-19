---
name: "spring-boot"
description: "Use this skill when the task is about Spring Boot application bootstrap, starter selection, externalized configuration, configuration properties, test strategy, Actuator operations, packaging, or Boot-level runtime wiring."
metadata:
  title: "Spring Boot"
  official_project_url: "https://spring.io/projects/spring-boot"
  reference_doc_urls:
    - "https://docs.spring.io/spring-boot/index.html"
    - "https://docs.spring.io/spring-boot/system-requirements.html"
    - "https://docs.spring.io/spring-boot/reference/index.html"
  version: "4.0.5"
---

Use this skill when the task is about Spring Boot application bootstrap, starter selection, externalized configuration, configuration properties, test strategy, Actuator operations, packaging, or Boot-level runtime wiring.

## Boundaries

Use `spring-boot` for Boot application structure, starter selection, auto-configuration usage, properties binding, profiles and config data, test strategy, Actuator, and packaging choices.

- Use narrower Spring skills for deep API details of MVC, Security, Data, Messaging, or other specialized projects once Boot wiring is already clear.
- Keep this skill focused on Boot-level composition, lifecycle, and operations rather than every Spring API surface.

## Common path

The ordinary Spring Boot job is:

1. Start with the smallest starter set that matches the application type and deployment shape.
2. Keep bootstrap and runtime lifecycle explicit with one entrypoint, one local run command, and predictable startup behavior.
3. Keep externalized configuration explicit, make profiles intentional, and bind durable settings with `@ConfigurationProperties`.
4. Follow simple Boot wiring conventions: one entrypoint, constructor injection, explicit runners for startup work, and no scattered `@Value` usage for durable settings.
5. Choose the narrowest test slice that proves the feature before escalating to `@SpringBootTest`.
6. Enable only the Actuator endpoints and operational signals the deployment actually needs.
7. Default to an executable jar first. Treat OCI images, war packaging, and native-image constraints as conditional branches.

## Dependency baseline

Use Boot dependency management and only the starters the application actually needs.

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.5</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## First safe configuration

### Minimal application shape

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Lifecycle shape

```java
@Bean
ApplicationRunner warmupRunner(CacheWarmupService warmupService) {
    return args -> warmupService.warm();
}
```

Use `ApplicationRunner` or `CommandLineRunner` for startup tasks that belong to the application lifecycle rather than bean construction.

### Configuration properties shape

```java
@Validated
@ConfigurationProperties("catalog")
public record CatalogProperties(@NotBlank String region, int pageSize) {
}
```

### Profile-specific configuration shape

```yaml
spring:
  config:
    activate:
      on-profile: prod
catalog:
  region: eu-west-1
```

### Actuator exposure shape

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

## Build and run path

Keep one local run path and one packaged run path explicit.

```bash
./mvnw spring-boot:run
```

```bash
java -jar target/app.jar
```

If the deployment baseline is container-native, keep the image build path explicit as a conditional branch rather than as an implicit default.

## Boot wiring conventions

- Keep one entrypoint annotated with `@SpringBootApplication`.
- Prefer constructor injection over field injection.
- Use `@ConfigurationProperties` for durable settings and reserve `@Value` for narrow one-off expressions.
- Keep startup work in runners or dedicated services, not in bean constructors.
- Let auto-configuration do the ordinary wiring before adding custom Boot infrastructure.

## Test strategy baseline

Choose the narrowest Boot test that proves the behavior.

| Need | Start here |
| --- | --- |
| MVC controller behavior | `@WebMvcTest` |
| data repository behavior | repository or slice test |
| full application integration | `@SpringBootTest` |

```java
@RestController
@RequestMapping("/api/greetings")
class GreetingController {
    @GetMapping
    Map<String, String> greet(@RequestParam(defaultValue = "world") String name) {
        return Map.of("message", "Hello " + name);
    }
}

@WebMvcTest(GreetingController.class)
class GreetingControllerTests {
    @Autowired
    MockMvc mvc;

    @Test
    void greeting() throws Exception {
        mvc.perform(get("/api/greetings").param("name", "Spring"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Hello Spring"));
    }
}
```

Open the testing reference when Boot wiring, local services, or service containers become the blocker.

## Production guardrails

- Keep starter choices small and intentional.
- Externalize credentials and environment-specific settings.
- Expose only the Actuator endpoints operations truly needs.
- Keep profile, config import, Docker Compose, and deployment assumptions explicit.
- Make startup, packaging, and local-service assumptions reproducible across local and deployment environments.

## References

- Open [references/autoconfiguration-diagnostics.md](references/autoconfiguration-diagnostics.md) when the task is about why Boot did or did not wire a bean.
- Open [references/config-data-order.md](references/config-data-order.md) when the blocker is config import order or imported config behavior.
- Open [references/property-precedence.md](references/property-precedence.md) when the blocker is conflicting values across property sources.
- Open [references/profile-activation.md](references/profile-activation.md) when the blocker is active-profile selection or profile-specific config loading.
- Open [references/configuration-properties-binding.md](references/configuration-properties-binding.md) when the blocker is `@ConfigurationProperties` binding behavior.
- Open [references/application-context-runner.md](references/application-context-runner.md) when the blocker is Boot-specific wiring diagnosis without starting the whole app.
- Open [references/testcontainers.md](references/testcontainers.md) when tests need a real backing service through Testcontainers.
- Open [references/service-connections.md](references/service-connections.md) when Boot should derive test service connection properties automatically.
- Open [references/docker-compose-local-wiring.md](references/docker-compose-local-wiring.md) when local development depends on Boot-managed Docker Compose lifecycle or explicit `spring.docker.compose.*` wiring.
- Open [references/health-groups.md](references/health-groups.md) when the task is about health groups.
- Open [references/probes.md](references/probes.md) when the task is about liveness or readiness probe behavior.
- Open [references/metrics.md](references/metrics.md) when the task is about metrics exports.
- Open [references/tracing.md](references/tracing.md) when the task is about tracing exports.
- Open [references/endpoint-exposure.md](references/endpoint-exposure.md) when the blocker is Actuator endpoint exposure policy.
- Open [references/sanitization.md](references/sanitization.md) when the blocker is sanitizing sensitive Actuator values.
- Open [references/layered-jars.md](references/layered-jars.md) when container rebuild speed depends on jar layers.
- Open [references/buildpacks.md](references/buildpacks.md) when the application should produce an OCI image without a Dockerfile.
- Open [references/dockerfiles.md](references/dockerfiles.md) when the platform requires explicit Dockerfile control.
- Open [references/war-packaging.md](references/war-packaging.md) when a traditional servlet container is a hard requirement.
- Open [references/aot-processing.md](references/aot-processing.md) when the blocker is AOT generation or runtime hints.
- Open [references/native-image.md](references/native-image.md) when the blocker is native-image build or runtime behavior.
