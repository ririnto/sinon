---
metadata:
  reference:
    Spring Boot:
      version: 4.1.1
      url:
        - https://docs.spring.io/spring-boot/reference/
        - https://docs.spring.io/spring-boot/reference/features/external-config.html
        - https://docs.spring.io/spring-boot/4.1.1/reference/testing/testcontainers.html
        - https://docs.spring.io/spring-boot/appendix/application-properties/index.html
        - https://docs.spring.io/spring-boot/appendix/deprecated-application-properties/index.html
        - https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes
        - https://github.com/spring-projects/spring-boot/tree/v4.1.1
name: spring-boot
description: >-
  Configure Spring Boot starters, properties, profiles, test slices, Actuator, and packaging, including Boot version migrations.
---

# Spring Boot

## Boundaries

Use `spring-boot` for Boot application structure, starter selection, auto-configuration usage, properties binding, profiles and config data, test strategy, Actuator, and packaging choices.

- Use narrower Spring skills for deep API details of MVC, Security, Data, Messaging, or other specialized projects once Boot wiring is already clear.
- Keep this skill focused on Boot-level composition, lifecycle, and operations rather than every Spring API surface.

## Task scope

Use the sections and references for the Boot behavior under change.
For new applications, choose the smallest starter set and default to an executable jar unless deployment requirements differ.
Existing applications do not need new bootstrap, testing, Actuator, or packaging work unless the task affects those concerns.

## Dependency baseline

Use Boot dependency management and only the starters the application actually needs.
The `4.1.1` parent below illustrates the documented baseline.
For a new parent, check `org.springframework.boot:spring-boot-starter-parent` on Maven Central for the latest stable release compatible with the project.
Keep an existing parent, BOM, or version-catalog pin unless the task authorizes changing it.

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
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
/**
 * Entry point that boots the application through Spring Boot's auto-configuration.
 */
public class Application {
    /**
     * Launches the Spring application context.
     */
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
@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
class CatalogConfiguration {
}

@Validated
@ConfigurationProperties("catalog")
/**
 * Holds the validated {@code catalog} configuration properties.
 */
public record CatalogProperties(@NotBlank String region, int pageSize) {
}
```

Register each `@ConfigurationProperties` type with `@EnableConfigurationProperties`, `@ConfigurationPropertiesScan`, or an equivalent Boot registration mechanism before injecting it.

### Profile-specific configuration shape

```yaml
spring:
  config:
    activate:
      on-profile: prod
catalog:
  region: eu-west-1
  page-size: 25
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

```sh
./mvnw spring-boot:run
```

```sh
java -jar target/app.jar
```

If the deployment baseline is container-native, keep the image build path explicit as a conditional branch rather than as an implicit default.

## Boot wiring conventions

- Keep one entrypoint annotated with `@SpringBootApplication`.
- Prefer constructor injection over field injection.
- Use `@ConfigurationProperties` for durable settings and reserve `@Value` for narrow one-off expressions.
- Keep configuration-binding types default-free: for a `@ConfigurationProperties` type, every bound property value comes from the configuration sources, and a default-free declaration paired with validation makes a missing required value fail startup.
- Keep startup work in runners or dedicated services, not in bean constructors.
- Let auto-configuration do the ordinary wiring before adding custom Boot infrastructure.

## Spring Boot 4.1 features

Spring Boot 4.1 adds Jackson multi-format properties, config-import encoding, lazy JDBC connection fetching, async JPA bootstrapping, `@Async` context propagation, `@RedisListener` auto-configuration, embedded LDAPS, and OpenTelemetry enhancements.
It also changes test-server behavior, HTTP client cookie handling, response compression, Docker Compose logging, build tooling, and `-DskipTests` AOT semantics.

Open [references/4.1-changes.md](references/4.1-changes.md) when the task uses a Spring Boot 4.1 feature or migrates from 4.0 to 4.1.

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

Open [references/application-context-runner.md](references/application-context-runner.md) when the blocker is Boot wiring without starting the whole application, and open [references/testcontainers.md](references/testcontainers.md) or [references/service-connections.md](references/service-connections.md) when tests need real local services.

## Production guardrails

- Keep starter choices small and intentional.
- Externalize credentials and environment-specific settings.
- Expose only the Actuator endpoints the operations team actually needs.
- Keep profile, config import, Docker Compose, and deployment assumptions explicit.
- Make startup, packaging, and local service assumptions reproducible across local and deployment environments.

## References

- Open [references/autoconfiguration-diagnostics.md](references/autoconfiguration-diagnostics.md) when the task is about why Boot did or did not wire a bean.
- Open [references/config-data-order.md](references/config-data-order.md) when the blocker is config import order or imported config behavior.
- Open [references/property-precedence.md](references/property-precedence.md) when the blocker is conflicting values across property sources.
- Open [references/profile-activation.md](references/profile-activation.md) when the blocker is active-profile selection or profile-specific config loading.
- Open [references/configuration-properties-binding.md](references/configuration-properties-binding.md) when the blocker is `@ConfigurationProperties` binding behavior.
- Open [references/application-context-runner.md](references/application-context-runner.md) when the blocker is Boot-specific wiring diagnosis without starting the whole app.
- Open [references/testcontainers.md](references/testcontainers.md) when tests need a real backing service through Testcontainers.
- Open [references/service-connections.md](references/service-connections.md) when Boot should derive test service connection properties automatically.
- Open [Docker Compose wiring](references/docker-compose-local-wiring.md) when local development depends on Boot-managed Docker Compose lifecycle or explicit `spring.docker.compose.*` wiring.
- Open [references/health-groups.md](references/health-groups.md) when the task is about health groups.
- Open [references/probes.md](references/probes.md) when the task is about liveness or readiness probe behavior.
- Open [references/metrics.md](references/metrics.md) when the task is about metrics exports.
- Open [references/tracing.md](references/tracing.md) when the task is about tracing exports, OTel SDK configuration, OTLP SSL bundles, or exemplar filtering.
- Open [references/endpoint-exposure.md](references/endpoint-exposure.md) when the blocker is Actuator endpoint exposure policy.
- Open [references/sanitization.md](references/sanitization.md) when the blocker is sanitizing sensitive Actuator values.
- Open [references/layered-jars.md](references/layered-jars.md) when container rebuild speed depends on jar layers.
- Open [references/buildpacks.md](references/buildpacks.md) when the application should produce an OCI image without a Dockerfile.
- Open [references/dockerfiles.md](references/dockerfiles.md) when the platform requires explicit Dockerfile control.
- Open [references/war-packaging.md](references/war-packaging.md) when a traditional servlet container is a hard requirement.
- Open [references/aot-processing.md](references/aot-processing.md) when the blocker is AOT generation or runtime hints.
- Open [references/native-image.md](references/native-image.md) when the blocker is native-image build or runtime behavior.
- Open [references/jackson-configuration.md](references/jackson-configuration.md) when the blocker is Jackson multi-format features, factory constraints, or HandlerInstantiator wiring.
- Open [references/4.1-changes.md](references/4.1-changes.md) when migrating from 4.0 to 4.1 or applying Spring Boot 4.1 behavior changes.
