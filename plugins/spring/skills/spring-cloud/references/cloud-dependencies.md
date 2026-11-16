# Spring Cloud dependencies

## Dependency baseline

Import the Spring Cloud BOM once and keep Spring Cloud modules versionless underneath it.
The `2025.1.3` BOM below illustrates the documented release train, not automatic support for Boot 4.1.x.
For a new BOM, check `org.springframework.cloud:spring-cloud-dependencies` on Maven Central and verify the stable train against the project's exact Boot release.
Keep an existing BOM or version-catalog pin unless the task authorizes changing it.

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
```

```text
Custom stream or task apps use their own Spring Boot + Spring Cloud dependencies.
```

### Feature-to-starter map

| Need | Starter or artifact |
| --- | --- |
| ConfigData client import | `spring-cloud-starter-config` |
| Service discovery client | the discovery implementation starter used by the platform |
| Load-balanced `RestClient` or Spring Interface Client (`lb://`) | `spring-cloud-starter-loadbalancer` |
| Circuit-breaker boundary with Resilience4j | `spring-cloud-starter-circuitbreaker-resilience4j` |
| Circuit-breaker boundary (Boot 4.x / Spring Framework 7 native) | `spring-cloud-starter-circuitbreaker-framework-retry` |
| Edge routing | add the Gateway starter from [references/gateway-routing.md](./gateway-routing.md) |
| Declarative HTTP clients | add the OpenFeign starter from [references/openfeign-clients.md](./openfeign-clients.md) when OpenFeign is the chosen client style |
| Broker-backed event transport | add the binder starter from [references/stream-binders.md](./stream-binders.md) |
| Vault-backed secrets import | add the Vault starter from [references/vault-config.md](./vault-config.md) |
| Kubernetes-native config or discovery | add the Kubernetes starter from [references/kubernetes-config.md](./kubernetes-config.md) or [references/kubernetes-discovery.md](./kubernetes-discovery.md) |
| Distributed refresh propagation | add the Bus transport starter from [references/bus-refresh.md](./bus-refresh.md) |
| Contract definition and provider verification | apply the verifier plugin from [references/contract-testing.md](./contract-testing.md) |
| Consumer-side executable stubs | add Stub Runner test support from [references/contract-testing.md](./contract-testing.md) |

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

Before configuring ConfigData, refresh, discovery, load balancing, resilience, compatibility verification, or Data Flow applications, read [configuration examples](./cloud-configuration.md).
Use `optional:` imports only when startup may continue without the remote config server; keep compatibility verification enabled unless deliberately overridden.
Use `@RefreshScope` only for beans that must rebind; apply retries and circuit breakers only at genuine remote boundaries.
