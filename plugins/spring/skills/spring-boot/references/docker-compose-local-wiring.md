# Spring Boot Docker Compose local wiring

Open this reference when local development depends on Boot-managed Docker Compose lifecycle or explicit `spring.docker.compose.*` wiring.

## Add the Boot Docker Compose module

Use Boot-managed Compose only when the application includes the Docker Compose support module.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-docker-compose</artifactId>
    <optional>true</optional>
</dependency>
```

```kotlin
dependencies {
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
}
```

With that module on the classpath, Boot can invoke the local `docker compose` lifecycle and derive connection details from the running services.

## Explicit `spring.docker.compose.*` wiring

```yaml
spring:
  docker:
    compose:
      file: ./compose.yaml
      lifecycle-management: start-and-stop
```

Use explicit properties when the local project layout or lifecycle policy differs from Boot's defaults.

## Docker Compose shape

```yaml
services:
  postgres:
    image: postgres:17
    ports:
      - "5432"
    environment:
      POSTGRES_DB: app
```

Boot connects through the mapped host port that Docker Compose publishes for the service.

- keep `compose.yaml` versioned with the application so the local service contract stays reviewable
- let Boot manage service discovery and lifecycle only after the Compose project itself is stable
- treat `spring.docker.compose.*` settings as explicit local-environment wiring, not hidden magic

## Gotchas

- Do not hide local infra assumptions behind implicit Compose startup.
- Do not use Boot-managed Compose unless the team already standardizes on Compose.
