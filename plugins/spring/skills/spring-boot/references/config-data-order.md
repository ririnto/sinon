# Spring Boot config data order

Open this reference when the blocker is config import order or imported config behavior.

```yaml
spring:
  config:
    import: optional:file:./config/local.yaml
```

Config imports now support explicit encoding. Property files default to ISO-8859-1 encoding unless overridden.

```properties
spring.config.import=classpath:db.properties[encoding=utf-8]
```

```yaml
spring:
  config:
    import: classpath:db.properties[encoding=utf-8]
```

Keep imported config explicit and reviewable. Hidden imports make local and deployment behavior drift.

## Validation rule

Verify the resolved value source before changing application code.
