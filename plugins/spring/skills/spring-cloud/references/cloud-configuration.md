# Spring Cloud configuration and task examples

## First safe configuration

### Distributed-system wiring

```sh
./mvnw test -Dtest=InventoryGatewayIntegrationTests
```

```sh
./gradlew test --tests InventoryGatewayIntegrationTests
```

### Data Flow orchestration

#### Register applications

The `3.2.1` registrations below illustrate one catalog snapshot.
Use the project's curated SCDF app catalog or check each artifact on Maven Central for its latest stable compatible release before registration.

```text
dataflow:>app register --name http --type source --uri maven://org.springframework.cloud.stream.app:http-source-rabbit:3.2.1
dataflow:>app register --name log --type sink --uri maven://org.springframework.cloud.stream.app:log-sink-rabbit:3.2.1
```

#### Create and deploy a stream

```text
dataflow:>stream create --name http-log --definition "http | log"
dataflow:>stream deploy --name http-log
```

#### Create and launch a task

```text
dataflow:>task create --name import-customers --definition "import-task"
dataflow:>task launch --name import-customers
```

### Config client shape

Requires `spring-cloud-starter-config`.

```yaml
spring:
  application:
    name: catalog-service
  config:
    import: optional:configserver:http://localhost:8888
```

Use `optional:` only when startup may continue without the config server.
If startup must stop when remote config is unavailable, keep the import explicit and use fail-fast semantics in the backend-specific branch.

Config profile validation can be disabled when the server must serve non-standard profile names:

```yaml
spring:
  cloud:
    config:
      server:
        profile-validation:
          enabled: false
```

Disable only when the Config Server intentionally serves profiles that are not standard Spring profiles.

### Refresh scope shape

```java
@RefreshScope
@Component
@ConfigurationProperties("catalog")
class CatalogProperties {
    private String region;

    /**
     * Returns the current {@code region} property value, rebound after each config refresh.
     */
    public String getRegion() {
        return this.region;
    }

    /**
     * Stores the newly bound {@code region} property value.
     */
    public void setRegion(String region) {
        this.region = region;
    }
}
```

Use `@RefreshScope` only for beans that must rebind after a config refresh rather than for ordinary immutable wiring.

> [!NOTE]
>
> Commons 5.0.2 fixes `DELETE /actuator/env` to correctly restore `ConfigurationProperties` beans to their initial values before rebinding.
> In earlier versions, a delete-and-refresh cycle could leave properties in an inconsistent state.

### Discovery client shape

Requires the discovery implementation starter used by the platform.

```java
@SpringBootApplication
@EnableDiscoveryClient
class CatalogApplication {
}
```

Discovery can auto-configure when a supported implementation is on the classpath, but explicit enablement is still useful when the distributed boundary must stay obvious in shared code.

### Load-balanced client shape

Requires `spring-cloud-starter-loadbalancer`.

```java
@Configuration
class ClientConfiguration {
    @Bean
    @LoadBalanced
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
```

Use the logical service id as the remote target boundary and let the load balancer resolve concrete instances at runtime.

### Compatibility verification shape

```yaml
spring:
  cloud:
    compatibility-verifier:
      enabled: true
```

Keep compatibility verification enabled unless the platform has a deliberate reason to override the release-train check.

### Resilience shape

Requires `spring-cloud-starter-circuitbreaker-resilience4j`.

```java
@Bean
Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
    return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id).circuitBreakerConfig(CircuitBreakerConfig.ofDefaults()).build());
}
```

Put retries or circuit breakers only around genuine remote call boundaries.
Do not treat resilience as a global default for local method calls.

> [!NOTE]
>
> CircuitBreaker 5.0.2 logs a warning when the default `TimeLimiter` configuration is used without explicit customization.
> Review and set `TimeLimiterConfig` deliberately instead of relying on defaults.

### Data Flow task app shape

```java
@SpringBootApplication
@EnableTask
/**
 * Entry point that registers this application as a Spring Cloud Task execution.
 */
public class ImportTaskApplication {
    /**
     * Launches the task application context.
     */
    public static void main(String[] args) {
        SpringApplication.run(ImportTaskApplication.class, args);
    }

    @Bean
    CommandLineRunner importCustomers(CustomerImporter importer) {
        return args -> importer.run();
    }
}
```

This task-app shape assumes a Spring Cloud Task application and therefore keeps `@EnableTask` explicit instead of presenting the app as a plain Boot command-line process.
