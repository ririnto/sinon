# Spring Boot endpoint exposure

Open this reference when the blocker is Actuator endpoint exposure policy.

Endpoints such as `env`, `configprops`, `mappings`, `conditions`, or `loggers` can be operationally useful but should be exposed deliberately.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

Keep high-risk endpoints out of the exposed set until the operations team explicitly asks for them.

## Validation rule

Verify the exposed endpoint list matches the intended operational surface and nothing more.
