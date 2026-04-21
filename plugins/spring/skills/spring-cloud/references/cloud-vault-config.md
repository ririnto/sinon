# Spring Cloud Vault config

Open this reference when the task depends on Spring Cloud Vault-backed ConfigData import and the blocker is the Vault context path, authentication mode, or fail-fast startup behavior.

## Starter shape

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
```

## Config import shapes

```yaml
spring:
  config:
    import: vault://
```

```yaml
spring:
  config:
    import: vault://secret/catalog
```

Use the broad `vault://` import when the application name and active profiles should drive the context lookup. Use an explicit context path only when the backend contract is intentionally fixed.

## Authentication choice

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
        role: catalog-service
        service-account-token-file: /var/run/secrets/kubernetes.io/serviceaccount/token
```

Use token auth for simple local bootstrap or operator-managed startup. Use Kubernetes auth only when the workload already runs in-cluster and the platform owns the service-account identity boundary.

## Fail-fast shape

```yaml
spring:
  cloud:
    vault:
      fail-fast: true
```

Enable fail-fast only when startup must stop if Vault-backed config is unavailable. If the service is expected to continue in degraded mode, keep fail-fast disabled and make the degraded behavior explicit in operations guidance.

## Gotchas

- Do not mix broad `vault://` import and explicit context paths casually in the same service.
- Do not use token auth in-cluster when Kubernetes auth is the real platform boundary.
- Do not enable fail-fast if the service is intentionally allowed to start without Vault config.

## Validation rule

Verify the imported Vault context paths, authentication mode, and fail-fast posture all match the actual startup contract for the target environment.
