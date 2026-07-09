---
name: spring-boot
description: >-
  Build Spring Boot applications with bootstrap, starter selection, externalized configuration, configuration properties, test strategy, Actuator, and packaging.
  Use when choosing starters, writing `@ConfigurationProperties` classes, configuring profiles, setting up test slices, packaging executable archives, configuring Jackson multi-format features, using @RedisListener, or managing Spring Boot 4.1 changes.
---

# Spring Boot

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
7. Default to an executable jar first.
   - Treat OCI images, war packaging, and native-image constraints as conditional branches.

## Dependency baseline

Use Boot dependency management and only the starters the application actually needs.

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
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
- Keep startup work in runners or dedicated services, not in bean constructors.
- Let auto-configuration do the ordinary wiring before adding custom Boot infrastructure.

## 4.1.0 new features

### Jackson multi-format configuration

Common read/write features across Jackson formats (JSON, CBOR, XML) are now configurable via `spring.jackson.read.*` and `spring.jackson.write.*` properties.
Factory-level read/write constraints use `spring.jackson.factory.*`.
Auto-configured mappers use a `HandlerInstantiator` that resolves handler instances from application context beans.

```yaml
spring:
  jackson:
    read:
      strict-duplicate-detection: true
    write:
      write-bigdecimal-as-plain: true
    factory:
      constraints:
        read:
          max-string-length: "256KB"
        write:
          max-nesting-depth: 50
```

For advanced customization, register `JsonMapperBuilderCustomizer`, `JsonFactoryBuilderCustomizer`, `CborFactoryBuilderCustomizer`, or `XmlFactoryBuilderCustomizer` beans.

Open [references/jackson-configuration.md](references/jackson-configuration.md) when the blocker is Jackson multi-format setup, factory constraints, or HandlerInstantiator wiring.

### Config import encoding

Config imports now support explicit encoding.
Files default to ISO-8859-1 encoding unless overridden.

```properties
spring.config.import=classpath:file.properties[encoding=utf-8]
```

### Lazy JDBC connection fetching

Defer physical JDBC connections until a statement is actually executed.

```yaml
spring:
  datasource:
    connection-fetch: lazy
```

When set to `lazy`, the auto-configured pooled `DataSource` is wrapped with `LazyConnectionDataSourceProxy`.

### Async JPA bootstrapping

Background bootstrap of `LocalContainerEntityManagerFactoryBean` for faster startup.

```yaml
spring:
  jpa:
    bootstrap: async
```

Requires an `AsyncTaskExecutor` bean.
If none is available when `async` is set, Boot will fail with a clear message.

### WebFlux HTML escaping

Application-wide default HTML escaping for WebFlux views.

```yaml
spring:
  webflux:
    default-html-escape: true
```

### HTTP client cookie handling

`TestRestTemplate` cookie handling now aligns with `RestTemplate`.
Configure via `withCookieHandling`, `RestTemplateBuilder`, or a property.

```yaml
spring:
  http:
    clients:
      cookie-handling: none
```

### @Async context propagation

Thread context is automatically propagated to `@Async` methods.

```yaml
spring:
  task:
    execution:
      propagate-context: true
```

### @AutoConfigureWebServer

Slice tests that need an embedded web server without the full application context.

```java
@AutoConfigureWebServer
class MyWebServerTests {
    @Autowired
    TomcatWebServerFactory webServerFactory;
    // ...
}
```

### Response compression MIME types

Additional MIME types beyond the defaults for HTTP response compression.

```yaml
server:
  compression:
    additional-mime-types: application/protobuf,application/octet-stream
```

### @RedisListener auto-configuration

Annotate beans with `@RedisListener` to create listener endpoints.
Boot auto-configures a `RedisMessageListenerContainer` when none is defined.

```java
@RedisListener("someChannel")
public void processMessage(String content) {
}
```

### Embedded LDAP SSL (LDAPS)

Enable SSL for the embedded in-memory LDAP server via an SSL bundle.

```yaml
spring:
  ldap:
    embedded:
      base-dn: dc=spring,dc=io
      ssl:
        bundle: example
```

### OpenTelemetry enhancements

Disable the OTel SDK while keeping propagators active.

```yaml
management:
  opentelemetry:
    enabled: false
    tracing:
      sampler: always_on
```

OTLP exporters support SSL bundles and metrics compression.

```yaml
management:
  otlp:
    metrics:
      export:
        compression-mode: gzip
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: https://collector:4318/v1/traces
          ssl:
            bundle: example
```

Open [references/tracing.md](references/tracing.md) for OTel configuration, OTLP SSL bundles, metrics compression, truststore cert metrics, and exemplar filtering.

### Docker Compose failure logging

On compose startup failure, Boot logs container output at the configured level.

```yaml
spring:
  docker:
    compose:
      start:
        log-level: debug
```

Docker Compose now supports `docker.elastic.co/elasticsearch/elasticsearch` services.

### Build updates

- `bootBuildImage --environment KEY=VALUE` for Gradle CLI environment overrides.
- `BuildInfo` task output changed to `META-INF/build-info.properties`.
  - Use the `filename` property to customize.
- Maven plugin loads `layers.xml` from classpath at `META-INF/spring/layers/<name>.xml`.
- `-DskipTests` no longer skips AOT.
  - Use `maven.test.skip` instead.

### Spock support restored

Spock 2.4 with Groovy 5 support is restored.
Add the `spring-boot-starter-test` dependency as usual; Spock tests work out of the box when `spock-spring` is on the classpath.

### Spring Batch with MongoDB

Auto-configuration for Spring Batch with MongoDB is provided by the dedicated `spring-boot-starter-batch-data-mongodb` starter, with `spring.batch.data.mongodb.*` properties controlling schema initialization and transaction validation.

## 4.1.0 changes

- **Layertools jar mode removed.** Use `tools` jar mode instead (`java -Djarmode=tools -jar app.jar`).
- **`-DskipTests` no longer skips AOT.** Use `-Dmaven.test.skip` for both test and AOT skip.
- **Changed Logback properties removed.** `logging.file.*` is gone; use `logging.logback.rollingpolicy.*`.
- **Derby support deprecated.** Deprecated in 4.1 and slated for removal; migrate to H2 or HSQLDB.
- **LiveReload in DevTools deprecated.** Deprecated in 4.1 with no replacement; still functional and disabled by default since 4.0.
- **Dynatrace V1 API deprecated.** Deprecated in 4.1; migrate to the V2 API.

Open [references/spring-boot-4.1-changes.md](references/spring-boot-4.1-changes.md) for the full migration guide.

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

## Output contract

Return:

1. The concrete configuration, code shape, or command for the stated Spring Boot feature.
2. The specific file or path changes if an implementation is required.
3. Any named reference to open when the path hits a blocker.
4. Explicit risks or version-sensitive behaviors that cannot be assumed backward-compatible.

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
- Open [references/spring-boot-4.1-changes.md](references/spring-boot-4.1-changes.md) when migrating from 4.0 to 4.1 or applying Spring Boot 4.1 behavior changes.
