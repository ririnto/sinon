# Spring Boot config data order

Open this reference when the blocker is config import order or imported config behavior.

```yaml
spring:
  config:
    import: optional:file:./config/local.yaml
```

Keep imported config explicit and reviewable. Hidden imports make local and deployment behavior drift.

## Validation rule

Verify the resolved value source before changing application code.
