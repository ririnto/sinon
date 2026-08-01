# Spring Cloud Kubernetes discovery

Open this reference when the task depends on Kubernetes-backed service discovery.

## Starter shape

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-kubernetes-client-discovery</artifactId>
</dependency>
```

Keep the starter versionless under the Spring Cloud BOM.

## Discovery shape

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

## Service-labels filter (5.0.2+, Fabric8)

Filter discovered services by Kubernetes labels:

```yaml
spring:
  cloud:
    kubernetes:
      discovery:
        service-labels:
          - app-group=catalog
          - tier=backend
```

Only services matching all specified labels appear in the discovery client catalog.

## Fabric8 listers-based discovery (5.0.2+)

Fabric8 discovery now uses Kubernetes informer-based listers instead of direct API calls, reducing API server load and improving consistency during high-churn deployments.

## Validation rule

Verify the resolved service ids come from the intended namespaces only.
