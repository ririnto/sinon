# Spring Boot profile activation

Open this reference when active-profile selection or profile-specific config loading is the blocker.

```properties
spring.profiles.active=prod
```

Put profile-specific values in `application-prod.yaml` using the canonical `spring.config.activate.on-profile` shape.
Prefer explicit profile activation and profile-specific files over hidden conditional code.

## Validation rule

Confirm the active profile and loaded config file set in the target runtime.
