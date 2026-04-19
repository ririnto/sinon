# Spring Cloud Kubernetes discovery

Open this reference when the task depends on Kubernetes-backed service discovery.

```java
@SpringBootApplication
@EnableDiscoveryClient
class Application {
}
```

```yaml
spring:
  cloud:
    kubernetes:
      discovery:
        namespaces:
          - team-a
          - team-b
```

## Validation rule

Verify the resolved service ids come from the intended namespaces only.
