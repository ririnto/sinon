# Spring Boot 4.1 changes

Open this reference when migrating from 4.0 to 4.1 or replacing features that changed.

## Added in 4.1

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

Open [jackson-configuration.md](jackson-configuration.md) when the blocker is Jackson multi-format setup, factory constraints, or HandlerInstantiator wiring.

### Config import encoding

Config imports now support explicit encoding.
Imported `.properties` files default to ISO-8859-1 encoding unless overridden.
This does not change YAML or other config formats.

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

Spring Boot 4.1 can bootstrap `LocalContainerEntityManagerFactoryBean` in the background for faster startup.

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

### Embedded web server tests

`@AutoConfigureWebServer` is not a test slice.
Use `@SpringBootTest` with an explicit web environment when the test needs the running server.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MyWebServerTests {
    @Autowired
    TestRestTemplate restTemplate;
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

Open [tracing.md](tracing.md) for OTel configuration, OTLP SSL bundles, metrics compression, truststore cert metrics, and exemplar filtering.

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

### Spock support restored

Spock 2.4 with Groovy 5 support is restored.
Add the `spring-boot-starter-test` dependency as usual.
Spock tests work out of the box when `spock-spring` is on the classpath.

### Spring Batch with MongoDB

Auto-configuration for Spring Batch with MongoDB is provided by the dedicated `spring-boot-starter-batch-data-mongodb` starter.
The `spring.batch.data.mongodb.*` properties control schema initialization and transaction validation.

## Removed

### Layertools jar mode

The `layertools` jar mode was removed.
Use `tools` jar mode which provides the same functionality.

```sh
java -Djarmode=tools -jar app.jar extract --layers --destination extracted
java -Djarmode=tools -jar app.jar list-layers
```

### Changed Logback properties

The following `logging.file.*` properties were removed.
Migrate to `logging.logback.rollingpolicy.*`.

| Removed property | Replacement |
| --- | --- |
| `logging.file.clean-history-on-start` | `logging.logback.rollingpolicy.clean-history-on-start` |
| `logging.file.max-history` | `logging.logback.rollingpolicy.max-history` |
| `logging.file.max-size` | `logging.logback.rollingpolicy.max-file-size` |
| `logging.file.total-size-cap` | `logging.logback.rollingpolicy.total-size-cap` |
| `logging.pattern.rolling-file-name` | `logging.logback.rollingpolicy.file-name-pattern` |

### `-DskipTests` no longer skips AOT

`-DskipTests` now only skips test execution, not AOT processing.
To skip both tests and AOT, use `-Dmaven.test.skip`.

```sh
./mvnw spring-boot:process-aot -Dmaven.test.skip
```

### Build updates

- `bootBuildImage --environment KEY=VALUE` for Gradle CLI environment overrides.
- `BuildInfo` task output changed to `META-INF/build-info.properties`.
  - Use the `filename` property to customize.
- Maven plugin loads `layers.xml` from classpath at `META-INF/spring/layers/<name>.xml`.

## Changed

### Derby support

Derby support is deprecated in 4.1 and slated for removal.
`org.springframework.boot.jdbc.DatabaseDriver.DERBY` and `org.springframework.boot.jdbc.EmbeddedDatabaseConnection.DERBY` are deprecated.
Migrate to H2 or HSQLDB.

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

### LiveReload in DevTools

`spring.devtools.livereload.enabled` and `spring.devtools.livereload.port` are deprecated in 4.1 with no replacement.
They are still functional, but Live Reload has been disabled by default since 4.0.
Set `spring.devtools.livereload.enabled=true` to re-enable it.

### Dynatrace V1 API

Dynatrace V1 API properties are deprecated in 4.1.
Use the V2 API instead.

| Deprecated property | Action |
| --- | --- |
| `management.dynatrace.metrics.export.v1.device-id` | Deprecated, use V2 |
| `management.dynatrace.metrics.export.v1.group` | Deprecated, use V2 |
| `management.dynatrace.metrics.export.v1.technology-type` | Deprecated, use V2 |

## Validation rule

Run the application with `-Ddebug` and check for property warnings before upgrading.
