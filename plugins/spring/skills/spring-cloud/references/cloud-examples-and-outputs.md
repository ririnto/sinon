# Spring Cloud implementation and output examples

## Implementation example

### Build shape

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.1.3</version>
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

### JUnit 6 validation shape

```java
@SpringBootTest
class InventoryGatewayIntegrationTests {
    @Autowired
    InventoryGateway gateway;

    @Test
    void logicalServiceIdCallReturnsItem() {
        ItemDto item = gateway.find("sku-1");
        assertAll(() -> assertNotNull(item), () -> assertEquals("sku-1", item.sku()));
    }
}
```

### Data Flow stream and task shapes

#### Stream definition and inspection

The `3.2.1` registrations below illustrate one catalog snapshot.
Use the project's curated SCDF app catalog or check each artifact on Maven Central for its latest stable compatible release before registration.

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

### Spring Interface Client with load balancing (Boot 4.x / 2025.1.x)

Spring Boot 4.0 introduced annotated HTTP interface clients backed by `RestClient` or `WebClient`.
Spring Cloud Commons 5.0 adds `lb://` scheme support on these clients and built-in circuit-breaker integration.

```java
interface InventoryClient {
    @GetExchange("/api/items/{sku}")
    ItemDto find(@PathVariable String sku);
}
```

```java
@Bean
InventoryClient inventoryClient(RestClient.Builder builder) {
    return HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(builder.baseUrl("lb://inventory-service").build()))
        .build()
        .createClient(InventoryClient.class);
}
```

Prefer Interface Clients for new Boot 4.x work when their annotated HTTP interface model fits.
Keep OpenFeign for existing Feign estates and for cases that depend on Feign-specific client configuration or fallback integrations.
Do not migrate solely to change client syntax.

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

## Data Flow verification

- Verify app registration points to the intended coordinates and versions.
- Verify stream or task definitions match the intended topology.
- Verify deployments or launches succeed with the chosen platform account.
- Verify one representative runtime inspection command returns the expected deployed or executed state.
- Verify task arguments and deployment properties stay aligned with the custom app contract.

## Data Flow production checklist

- Keep registered app coordinates versioned and reviewable.
- Keep stream DSL and task definitions small enough to operate safely.
- Separate platform deployment properties from business-app defaults.
- Verify schedules, platform accounts, and task arguments before promoting to production.
