# Spring Cloud Vault fail-fast

Open this reference when startup behavior must fail fast on unavailable Vault config.

```yaml
spring:
  cloud:
    vault:
      fail-fast: true
```

Use fail-fast only when startup must stop if Vault-backed config is unavailable.

## Gotchas

- Do not enable fail-fast if the service is expected to start in degraded mode without Vault config.
