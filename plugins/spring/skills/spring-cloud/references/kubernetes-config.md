# Spring Cloud Kubernetes config

Open this reference when the task depends on ConfigMap-backed Spring Cloud Kubernetes config import.

```yaml
spring:
  config:
    import: kubernetes:
```

```yaml
spring:
  cloud:
    kubernetes:
      config:
        sources:
          - name: orders-config
```

## Validation rule

Verify the effective ConfigMap source names and namespaces in the target cluster.
