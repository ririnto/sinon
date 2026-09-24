# Spring Boot profile activation

Open this reference when active-profile selection or profile-specific config loading is the blocker.

Set active profiles in a non-profile-specific `application.yaml` document.

```yaml
spring:
  profiles:
    active: prod
```

Put profile-specific values in `application-prod.yaml`.
Use `spring.config.activate.on-profile` to gate a document inside a multi-document configuration file.

```yaml
---
spring:
  config:
    activate:
      on-profile: prod
```

Prefer explicit profile activation and profile-specific files over hidden conditional code.

## Validation rule

Confirm the active profile and loaded config file set in the target runtime.
