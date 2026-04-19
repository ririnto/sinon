# Spring Cloud Vault config import

Open this reference when the task depends on Spring Cloud Vault ConfigData import.

```yaml
spring:
  config:
    import: vault://
```

```yaml
spring:
  config:
    import: vault://secret/orders
```

## Validation rule

Verify the imported Vault context paths match the application name and mounted backend contract.
