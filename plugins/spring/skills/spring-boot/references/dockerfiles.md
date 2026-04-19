# Spring Boot Dockerfiles

Open this reference when the platform requires explicit Dockerfile control.

```dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Prefer multi-stage builds when the jar is built inside the image pipeline.

## Validation rule

Verify the Dockerfile entrypoint, working directory, and artifact copy path match the actual build output.
