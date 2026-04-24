---
name: "spring-cloud"
description: "Implement Spring Cloud distributed-system building blocks for ConfigData integration, refresh-aware configuration, service discovery, load-balanced downstream calls, and circuit-breaker boundaries. Use this skill when implementing Spring Cloud distributed-system building blocks such as ConfigData integration, refresh-aware configuration, service discovery, load-balanced downstream calls, circuit-breaker boundaries, and release-train-aligned dependency management."
metadata:
  title: "Spring Cloud"
  official_project_url: "https://spring.io/projects/spring-cloud"
  reference_doc_urls:
    - "https://docs.spring.io/spring-cloud-config/reference/"
    - "https://docs.spring.io/spring-cloud-gateway/reference/"
    - "https://docs.spring.io/spring-cloud-openfeign/reference/"
    - "https://docs.spring.io/spring-cloud-stream/reference/"
    - "https://docs.spring.io/spring-cloud-contract/reference/"
  version: "2025.1.1"
---

Use this skill when implementing Spring Cloud distributed-system building blocks such as ConfigData integration, refresh-aware configuration, service discovery, load-balanced downstream calls, circuit-breaker boundaries, and release-train-aligned dependency management.

The current Boot 4.x Spring Cloud release-train line is 2025.1.1 (Oakwood). Spring Cloud 2025.0.2 is a parallel Boot 3.5.x line, not a newer replacement, so the common path in this skill stays anchored to 2025.1.1 unless the project is intentionally on the 3.5.x generation.

## Boundaries

Use `spring-cloud` for release-train-aligned distributed application wiring, external configuration through ConfigData, refresh-aware configuration, service discovery, load-balanced downstream calls, and resilience patterns.

- Use `spring-cloud-data-flow` for SCDF stream and task orchestration.
- Use narrower Spring Cloud branches when the task is specifically about Gateway, OpenFeign, Stream binders, Contract, Bus, Kubernetes, or Vault behavior.

## Surface map

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| ConfigData client | the service needs externalized configuration through ConfigData | Vault-backed config is the real blocker in [references/cloud-vault-config.md](references/cloud-vault-config.md) or Kubernetes-backed config is the blocker in [references/kubernetes-config.md](references/kubernetes-config.md) |
| Refresh-aware configuration | one bean must rebind after refresh | distributed refresh propagation is the blocker in [references/bus-refresh.md](references/bus-refresh.md) |
| Service discovery and load-balanced clients | one service calls another by logical service id | Kubernetes-backed discovery is the blocker in [references/kubernetes-discovery.md](references/kubernetes-discovery.md) or declarative clients are clearer in [references/openfeign-clients.md](references/openfeign-clients.md) |
| Circuit-breaker boundary | one remote call needs resilience | gateway routing is the real boundary in [references/gateway-routing.md](references/gateway-routing.md) |
| Contract verification | provider-consumer compatibility is the real job | open [references/contract-testing.md](references/contract-testing.md) |
| Stream binders | the service actually publishes to or consumes from a broker | open [references/stream-binders.md](references/stream-binders.md) |
| Function catalog | the task is specifically about function beans or composition | open [references/function-catalog.md](references/function-catalog.md) |

## Common path

The ordinary Spring Cloud job is:

1. Align the application with one Spring Cloud release train.
2. Add only the starters the service actually needs for ConfigData, service discovery, load balancing, or resilience.
3. Keep service names, config import locations, and logical service ids explicit.
4. Wire one discovery-backed downstream client and add resilience only where the remote call justifies it.
5. Test one healthy path and one representative downstream-failure path end to end.

## Dependency baseline

Import the Spring Cloud BOM once and keep Spring Cloud modules versionless underneath it.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.1.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Feature-to-starter map

| Need | Starter or artifact |
| --- | --- |
| ConfigData client import | `spring-cloud-starter-config` |
| Service discovery client | the discovery implementation starter used by the platform |
| Load-balanced `RestClient` or `RestTemplate` | `spring-cloud-starter-loadbalancer` |
| Circuit-breaker boundary with Resilience4j | `spring-cloud-starter-circuitbreaker-resilience4j` |
| Edge routing | add the Gateway starter from [references/gateway-routing.md](references/gateway-routing.md) |
| Declarative HTTP clients | add the OpenFeign starter from [references/openfeign-clients.md](references/openfeign-clients.md) |
| Broker-backed event transport | add the binder starter from [references/stream-binders.md](references/stream-binders.md) |
| Distributed refresh propagation | add the Bus transport starter from [references/bus-refresh.md](references/bus-refresh.md) |
| Contract verification | add the contract plugin and test support from [references/contract-testing.md](references/contract-testing.md) |

### Ordinary baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-config</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-loadbalancer</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
    </dependency>
</dependencies>
```

Keep the discovery implementation starter, Gateway starter, OpenFeign starter, and bus or binder transports out of the common path unless the service truly needs them.

## First safe configuration

### First safe commands

```bash
./mvnw test -Dtest=InventoryGatewayIntegrationTests
```

```bash
./gradlew test --tests InventoryGatewayIntegrationTests
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

Use `optional:` only when startup may continue without the config server. If startup must stop when remote config is unavailable, keep the import explicit and use fail-fast semantics in the backend-specific branch.

### Refresh scope shape

```java
@RefreshScope
@Component
@ConfigurationProperties("catalog")
class CatalogProperties {
    private String region;

    public String getRegion() {
        return this.region;
    }

    public void setRegion(String region) {
        this.region = region;
    }
}
```

Use `@RefreshScope` only for beans that must rebind after a config refresh rather than for ordinary immutable wiring.

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
Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
    return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id).circuitBreakerConfig(CircuitBreakerConfig.ofDefaults()).build());
}
```

Put retries or circuit breakers only around genuine remote call boundaries. Do not treat resilience as a global default for local method calls.

## Coding procedure

1. Keep release-train and Boot alignment explicit in the build.
2. Choose the smallest Spring Cloud module set that solves the current distributed-system need.
3. Keep service names, route ids, and external config import locations stable.
4. Use logical service ids consistently across discovery-backed and load-balanced calls.
5. Add retries and circuit breakers only around genuine remote boundaries.
6. Keep gateway policy and downstream client policy separate so each remains understandable.
7. Test both the healthy path and one representative downstream-failure path.

## Implementation example

### Build shape

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.1.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-config</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-loadbalancer</artifactId>
    </dependency>
</dependencies>
```

### Application configuration shape

```yaml
spring:
  application:
    name: catalog-service
  config:
    import: optional:configserver:http://localhost:8888
```

### Discovery-backed downstream call

```java
@SpringBootApplication
@EnableDiscoveryClient
class CatalogApplication {
}

@Configuration
class ClientConfiguration {
    @Bean
    @LoadBalanced
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}

@Service
class InventoryGateway {
    private final RestClient restClient;

    InventoryGateway(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://inventory-service").build();
    }

    ItemDto find(String sku) {
        return this.restClient.get().uri("/api/items/{sku}", sku).retrieve().body(ItemDto.class);
    }
}
```

### JUnit 5 validation shape

```java
@SpringBootTest
class InventoryGatewayIntegrationTests {
    @Autowired
    InventoryGateway gateway;

    @Test
    void logicalServiceIdCallReturnsItem() {
        ItemDto item = gateway.find("sku-1");
        assertAll(
            () -> assertNotNull(item),
            () -> assertEquals("sku-1", item.sku())
        );
    }
}
```

## Output and configuration shapes

### Service-id shape (`spring.application.name` and discovery ids)

```text
catalog-service
inventory-service
```

### Config import shape (`spring.config.import`)

```text
optional:configserver:http://localhost:8888
vault://secret/catalog
kubernetes:
```

### Load-balanced URI shape (gateway routes or client base URLs)

```text
lb://catalog-service
http://inventory-service
```

## Testing checklist

- Verify the service boots with the intended release-train-managed dependencies.
- Verify external configuration resolves from the intended source.
- Verify one discovery-backed downstream call targets the expected logical service id.
- Verify one representative load-balanced remote call succeeds against the intended logical service.
- Verify one representative downstream failure path exercises the chosen retry or circuit-breaker rule.

## Production checklist

- Keep Boot and Spring Cloud release-train compatibility explicit.
- Keep service ids, route ids, and config import locations stable.
- Add resilience features only around actual remote boundaries.
- Keep discovery-backed and load-balanced client behavior observable.
- Treat distributed-system wiring tests as part of the operational compatibility surface.

## References

Use these only when the task moves beyond the ordinary config, refresh, discovery, and downstream-client path.

- Open [references/gateway-routing.md](references/gateway-routing.md) when the service must own an edge routing boundary instead of calling downstream services directly.
- Open [references/openfeign-clients.md](references/openfeign-clients.md) when declarative HTTP clients are clearer than direct `RestClient` code.
- Open [references/stream-binders.md](references/stream-binders.md) when the task is specifically about Stream binder wiring.
- Open [references/function-catalog.md](references/function-catalog.md) when the task is specifically about Spring Cloud Function beans or composition.
- Open [references/contract-testing.md](references/contract-testing.md) when the task is specifically about Spring Cloud Contract.
- Open [references/kubernetes-config.md](references/kubernetes-config.md) when config import or reload is backed by Kubernetes sources.
- Open [references/kubernetes-discovery.md](references/kubernetes-discovery.md) when service discovery is backed by Kubernetes namespaces and services.
- Open [references/bus-refresh.md](references/bus-refresh.md) when the platform actually needs distributed refresh or event propagation.
- Open [references/cloud-vault-config.md](references/cloud-vault-config.md) when config import is backed by Vault and the blocker is authentication mode or fail-fast behavior.
