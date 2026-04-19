# Spring Cloud Kubernetes reload

Open this reference when the task depends on config reload behavior from Kubernetes sources.

```yaml
spring:
  cloud:
    kubernetes:
      config:
        reload:
          enabled: true
          mode: EVENT
```

## Gotchas

- Do not enable reload until the source configuration set and rebinding expectations are stable.
