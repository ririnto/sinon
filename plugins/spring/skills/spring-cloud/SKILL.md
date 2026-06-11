---
name: spring-cloud
description: >-
  Implement Spring Cloud distributed-system building blocks for ConfigData integration, refresh-aware configuration, service discovery, load-balanced downstream calls, and circuit-breaker boundaries. Use when configuring Spring Cloud Config client or server, registering services with Eureka or Consul, wiring load-balanced `RestClient` or `WebClient`, applying circuit-breaker patterns with Resilience4J, or maintaining Spring Cloud Data Flow streams and tasks with app registration, stream DSL, task launch, schedules, platform accounts, and pipeline operations.
metadata:
  title: "Spring Cloud"
  official-project-url: "https://spring.io/projects/spring-cloud"
  reference-doc-urls:
    - "https://docs.spring.io/spring-cloud-config/reference/"
    - "https://docs.spring.io/spring-cloud-gateway/reference/"
    - "https://docs.spring.io/spring-cloud-openfeign/reference/"
    - "https://docs.spring.io/spring-cloud-stream/reference/"
    - "https://docs.spring.io/spring-cloud-contract/reference/"
    - "https://dataflow.spring.io/docs/"
  version: "2025.1.1"
---

The current Boot 4.x Spring Cloud release-train line is 2025.1.1 (Oakwood). Spring Cloud 2025.0.2 is a parallel Boot 3.5.x line, not a newer replacement, so the common path in this skill stays anchored to 2025.1.1 unless the project is intentionally on the 3.5.x generation.

## Boundaries

Use `spring-cloud` for release-train-aligned distributed application wiring, external configuration through ConfigData, refresh-aware configuration, service discovery, load-balanced downstream calls, resilience patterns, and Spring Cloud Data Flow orchestration.

- Use narrower Spring Cloud branches when the task is specifically about Gateway, OpenFeign, Stream binders, Contract, Bus, Kubernetes, or Vault behavior.
- Data Flow content in this skill is limited to existing stream and task estates, runtime operations, and migration support. Spring Cloud Data Flow is in maintenance mode; verify the current project status before recommending SCDF for greenfield orchestration.
- In-process integration flows inside one application are outside this skill's scope.
- Batch-job or launchable-task internals are outside this skill's scope. SCDF is the orchestration and platform layer around those apps.

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
| Data Flow orchestration | the job is registering stream or task apps, composing stream DSL pipelines, launching tasks, or managing schedules on an existing SCDF estate | the blocker is in [references/data-flow-platform-setup.md](references/data-flow-platform-setup.md), [references/data-flow-app-registration-metadata.md](references/data-flow-app-registration-metadata.md), [references/data-flow-platform-accounts.md](references/data-flow-platform-accounts.md), or another Data Flow reference below |

## Common path

### Distributed-system wiring

The ordinary Spring Cloud job is:

1. Align the application with one Spring Cloud release train.
2. Add only the starters the service actually needs for ConfigData, service discovery, load balancing, or resilience.
3. Keep service names, config import locations, and logical service ids explicit.
4. Wire one discovery-backed downstream client and add resilience only where the remote call justifies it.
5. Test one healthy path and one representative downstream-failure path end to end.

### Data Flow orchestration

The ordinary Spring Cloud Data Flow job is:

1. Decide whether the workload is a long-running stream or a launchable task.
2. Register the source, processor, sink, or task applications with explicit coordinates.
3. Create the stream or task definition and add only the deployment properties actually needed.
4. Deploy or launch it and inspect status, logs, and metrics.
5. For streams, verify the Skipper package state before updating or rolling back a deployed definition.
6. Keep the topology, app versions, and platform account assumptions explicit.

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

SCDF is primarily an external orchestration platform, not a business-app dependency. Custom apps normally depend on Spring Cloud Stream or Spring Cloud Task rather than an SCDF library.

The current stable SCDF server line is `2.11.5`. Keep examples on the stable GA line unless the task explicitly targets a newer milestone or snapshot, and treat that stable line as maintenance-oriented rather than a default greenfield recommendation.

```text
SCDF server and shell are operated outside the business application.
Custom stream or task apps use their own Spring Boot + Spring Cloud dependencies.
```

### Feature-to-starter map

| Need | Starter or artifact |
| --- | --- |
| ConfigData client import | `spring-cloud-starter-config` |
| Service discovery client | the discovery implementation starter used by the platform |
| Load-balanced `RestClient` (preferred) or `RestTemplate` (maintenance) | `spring-cloud-starter-loadbalancer` |
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

### Distributed-system wiring

```sh
./mvnw test -Dtest=InventoryGatewayIntegrationTests
```

```sh
./gradlew test --tests InventoryGatewayIntegrationTests
```

### Data Flow orchestration

#### Register applications

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

### Data Flow task app shape

```java
@SpringBootApplication
@EnableTask
public class ImportTaskApplication {
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

## Coding procedure

### Distributed-system wiring

1. Keep release-train and Boot alignment explicit in the build.
2. Choose the smallest Spring Cloud module set that solves the current distributed-system need.
3. Keep service names, route ids, and external config import locations stable.
4. Use logical service ids consistently across discovery-backed and load-balanced calls.
5. Add retries and circuit breakers only around genuine remote boundaries.
6. Keep gateway policy and downstream client policy separate so each remains understandable.
7. Test both the healthy path and one representative downstream-failure path.

### Data Flow orchestration

1. Pick stream versus task first because deployment, lifecycle, and observability differ.
2. Keep application registration coordinates explicit and versioned.
3. Put platform-specific settings in deployment properties rather than baking them into app code.
4. Keep stream DSL definitions small and composable before introducing large topologies.
5. Treat task parameters and schedule definitions as operational contracts.
6. Verify deployment or launch status after every topology change.

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

### Data Flow stream and task shapes

#### Stream definition and inspection

```text
dataflow:>app register --name http --type source --uri maven://org.springframework.cloud.stream.app:http-source-rabbit:3.2.1
dataflow:>app register --name log --type sink --uri maven://org.springframework.cloud.stream.app:log-sink-rabbit:3.2.1
dataflow:>stream create --name http-log --definition "http | log"
dataflow:>stream deploy --name http-log
dataflow:>stream list
dataflow:>runtime apps
```

#### Task orchestration

```text
dataflow:>task create --name import-customers --definition "import-task"
dataflow:>task launch --name import-customers --arguments "--input=file:/data/customers.csv"
dataflow:>task execution list
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

### Data Flow output shapes

- One pinned app-registration command or curated import file
- One stream or task definition
- One platform-specific deployment-properties set only when needed
- One runtime verification command sequence after deploy or launch

Stream DSL shape:

```text
http | log
```

Task launch argument shape:

```text
--input=file:/data/customers.csv
```

Runtime inspection shape:

```text
runtime apps
task execution list
```

## Testing checklist

### Distributed-system wiring

- Verify the service boots with the intended release-train-managed dependencies.
- Verify external configuration resolves from the intended source.
- Verify one discovery-backed downstream call targets the expected logical service id.
- Verify one representative load-balanced remote call succeeds against the intended logical service.
- Verify one representative downstream failure path exercises the chosen retry or circuit-breaker rule.

### Data Flow orchestration

- Verify app registration points to the intended coordinates and versions.
- Verify stream or task definitions match the intended topology.
- Verify deployments or launches succeed with the chosen platform account.
- Verify one representative runtime inspection command returns the expected deployed or executed state.
- Verify task arguments and deployment properties stay aligned with the custom app contract.

## Production checklist

### Distributed-system wiring

- Keep Boot and Spring Cloud release-train compatibility explicit.
- Keep service ids, route ids, and config import locations stable.
- Add resilience features only around actual remote boundaries.
- Keep discovery-backed and load-balanced client behavior observable.
- Treat distributed-system wiring tests as part of the operational compatibility surface.

### Data Flow orchestration

- Keep registered app coordinates versioned and reviewable.
- Keep stream DSL and task definitions small enough to operate safely.
- Separate platform deployment properties from business-app defaults.
- Verify schedules, platform accounts, and task arguments before promoting to production.
- Treat SCDF shell commands and topology definitions as part of the operational compatibility surface.

## References

Use these only when the task moves beyond the ordinary config, refresh, discovery, and downstream-client path.

### Distributed-system wiring references

- Open [references/gateway-routing.md](references/gateway-routing.md) when the service must own an edge routing boundary instead of calling downstream services directly.
- Open [references/openfeign-clients.md](references/openfeign-clients.md) when declarative HTTP clients are clearer than direct `RestClient` code.
- Open [references/stream-binders.md](references/stream-binders.md) when the task is specifically about Stream binder wiring.
- Open [references/function-catalog.md](references/function-catalog.md) when the task is specifically about Spring Cloud Function beans or composition.
- Open [references/contract-testing.md](references/contract-testing.md) when the task is specifically about Spring Cloud Contract.
- Open [references/kubernetes-config.md](references/kubernetes-config.md) when config import or reload is backed by Kubernetes sources.
- Open [references/kubernetes-discovery.md](references/kubernetes-discovery.md) when service discovery is backed by Kubernetes namespaces and services.
- Open [references/bus-refresh.md](references/bus-refresh.md) when the platform actually needs distributed refresh or event propagation.
- Open [references/cloud-vault-config.md](references/cloud-vault-config.md) when config import is backed by Vault and the blocker is authentication mode or fail-fast behavior.

### Data Flow references

- Open [references/data-flow-platform-setup.md](references/data-flow-platform-setup.md) when the blocker is installing SCDF or choosing the local, Kubernetes, or Cloud Foundry runtime target before any app registration.
- Open [references/data-flow-app-registration-metadata.md](references/data-flow-app-registration-metadata.md) when the blocker is versioned coordinates, bulk import, metadata, or curating registered app catalogs.
- Open [references/data-flow-platform-accounts.md](references/data-flow-platform-accounts.md) when the blocker is choosing or configuring the target platform account for a deploy or launch.
- Open [references/data-flow-schedules.md](references/data-flow-schedules.md) when the blocker is recurring SCDF-managed task execution.
- Open [references/data-flow-composed-tasks.md](references/data-flow-composed-tasks.md) when one task is not enough and SCDF must orchestrate multiple task apps.
- Open [references/data-flow-runtime-operations.md](references/data-flow-runtime-operations.md) when the blocker is deeper runtime inspection, stream update or rollback, undeploy, destroy, or post-change operational verification.
- Open [references/data-flow-troubleshooting.md](references/data-flow-troubleshooting.md) when registration, deployment, launch, logging, or platform behavior is failing and the diagnosis order is unclear.
