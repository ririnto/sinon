# Spring Boot Docker Compose local wiring

Open this reference when local development depends on Boot-managed Docker Compose lifecycle or explicit `spring.docker.compose.*` wiring.

## Docker Compose shape

```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: app
```

- keep `compose.yaml` versioned with the application so the local service contract stays reviewable
- let Boot manage service discovery and lifecycle only after the Compose project itself is stable
- treat `spring.docker.compose.*` settings as explicit local-environment wiring, not hidden magic

## Gotchas

- Do not hide local infra assumptions behind implicit Compose startup.
- Do not use Boot-managed Compose unless the team already standardizes on Compose.
