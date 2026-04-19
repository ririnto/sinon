# Spring Boot profile activation

Open this reference when active-profile selection or profile-specific config loading is the blocker.

```properties
spring.profiles.active=prod
```

```yaml
spring:
  config:
    activate:
      on-profile: prod
catalog:
  region: eu-west-1
```

Prefer explicit profile activation and profile-specific files over hidden conditional code.

## Validation rule

Confirm the active profile and loaded config file set in the target runtime.
