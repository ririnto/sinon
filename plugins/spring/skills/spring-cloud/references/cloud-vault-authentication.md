# Spring Cloud Vault authentication

Open this reference when the task depends on Spring Cloud Vault authentication mode selection.

```yaml
spring:
  cloud:
    vault:
      uri: https://localhost:8200
      authentication: TOKEN
      token: 00000000-0000-0000-0000-000000000000
```

```yaml
spring:
  cloud:
    vault:
      authentication: KUBERNETES
      kubernetes:
        role: orders-service
        service-account-token-file: /var/run/secrets/kubernetes.io/serviceaccount/token
```

## Decision point

Use token auth for simple bootstrap and Kubernetes auth only when the workload already runs in-cluster.
