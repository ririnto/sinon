---
name: "spring-cloud"
description: "Use this skill when implementing Spring Cloud distributed-system building blocks such as ConfigData integration, refresh-aware configuration, service discovery, load-balanced downstream calls, circuit-breaker boundaries, and release-train-aligned dependency management."
metadata:
  title: "Spring Cloud"
  official_project_url: "https://spring.io/projects/spring-cloud"
  reference_doc_urls:
    - "https://docs.spring.io/spring-cloud-release/reference/index.html"
    - "https://docs.spring.io/spring-cloud-config/reference/"
    - "https://docs.spring.io/spring-cloud-gateway/reference/"
    - "https://docs.spring.io/spring-cloud-openfeign/reference/"
    - "https://docs.spring.io/spring-cloud-stream/reference/"
    - "https://docs.spring.io/spring-cloud-contract/reference/"
  version: "2025.1.1"
---

Use this skill when implementing Spring Cloud distributed-system building blocks such as ConfigData integration, refresh-aware configuration, service discovery, load-balanced downstream calls, circuit-breaker boundaries, and release-train-aligned dependency management.

## Boundaries

Use `spring-cloud` for release-train-aligned distributed application wiring, external configuration through ConfigData, refresh-aware configuration, service discovery, load-balanced downstream calls, and resilience patterns.

- Use `spring-cloud-data-flow` for SCDF stream and task orchestration.
- Use narrower Spring Cloud skills or references when the task is specifically about Gateway, Stream, Contract, Kubernetes, Bus, or Vault behavior.

## Common path

The ordinary Spring Cloud job is:

1. Align the application with one Spring Cloud release train.
2. Decide whether the service needs externalized configuration through ConfigData, refresh-aware configuration, outbound service discovery, or a load-balanced downstream client.
3. Keep service names, routes, and config import locations explicit.
4. Add resilience boundaries such as retries or circuit breakers only where remote calls justify them.
5. Test one representative remote-call or route path end to end.

## Dependency baseline

Import the Spring Cloud BOM and add only the starters the service actually needs for config, discovery, load balancing, or resilience.

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
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
    </dependency>
</dependencies>
```

Only keep the modules the current service actually uses.

## First safe configuration

### Config client shape

```yaml
spring:
  application:
    name: catalog-service
  config:
    import: optional:configserver:http://localhost:8888
```

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

```java
@SpringBootApplication
@EnableDiscoveryClient
class CatalogApplication {
}
```

Discovery can auto-configure when a supported implementation is on the classpath, but explicit enablement is still useful when the boundary must stay obvious in shared code.

### Service discovery and load-balanced client shape

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
4. Use logical service ids consistently across load-balanced clients and discovery-backed calls.
5. Add retries and circuit breakers only around genuine remote call boundaries.
6. Keep gateway policy and downstream client policy separate so each remains understandable.
7. Test both the healthy path and one representative downstream-failure path.

## Implementation examples

### Config client

```yaml
spring:
  application:
    name: catalog-service
  config:
    import: optional:configserver:http://localhost:8888
```

### Load-balanced downstream client

```java
@Configuration
class ClientConfiguration {
    @Bean
    @LoadBalanced
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}

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

## Output and configuration shapes

### Service-id shape

```text
catalog-service
inventory-service
```

### Config import shape

```text
optional:configserver:http://localhost:8888
```

### Load-balanced URI shape

```text
lb://catalog-service
```

## Testing checklist

- Verify the service boots with the intended release-train-managed dependencies.
- Verify external configuration resolves from the intended source.
- Verify discovery-backed downstream calls target the expected logical service id.
- Verify one representative load-balanced remote call succeeds against the intended logical service.
- Verify one representative downstream failure path exercises the chosen retry or resilience rule.

## Production checklist

- Keep Boot and Spring Cloud release-train compatibility explicit.
- Keep service ids and config import locations stable.
- Add resilience features only around actual remote boundaries.
- Keep discovery-backed and load-balanced client behavior observable.
- Treat distributed-system wiring tests as part of the operational compatibility surface.

## References

Use these only when the task moves beyond the ordinary config, discovery, and downstream-client path.

- Open [references/gateway-routing.md](references/gateway-routing.md) when the task is specifically about gateway routing or moving from direct downstream calls to an edge routing boundary.
- Open [references/openfeign-clients.md](references/openfeign-clients.md) when the task is specifically about declarative OpenFeign clients.
- Open [references/stream-binders.md](references/stream-binders.md) when the task is about Spring Cloud Stream binder wiring.
- Open [references/function-catalog.md](references/function-catalog.md) when the task is about Spring Cloud Function beans or function composition.
- Open [references/contract-testing.md](references/contract-testing.md) when the task is specifically about Spring Cloud Contract.
- Open [references/kubernetes-config.md](references/kubernetes-config.md) when the task depends on ConfigMap-backed Spring Cloud Kubernetes config import.
- Open [references/kubernetes-discovery.md](references/kubernetes-discovery.md) when the task depends on Kubernetes-backed service discovery.
- Open [references/kubernetes-reload.md](references/kubernetes-reload.md) when the task depends on config reload behavior from Kubernetes sources.
- Open [references/bus-refresh.md](references/bus-refresh.md) when the task depends on Spring Cloud Bus behavior.
- Open [references/cloud-vault-config-import.md](references/cloud-vault-config-import.md) when the task depends on Spring Cloud Vault ConfigData import.
- Open [references/cloud-vault-authentication.md](references/cloud-vault-authentication.md) when the task depends on Spring Cloud Vault authentication mode selection.
- Open [references/cloud-vault-fail-fast.md](references/cloud-vault-fail-fast.md) when startup behavior must fail fast on unavailable Vault config.
