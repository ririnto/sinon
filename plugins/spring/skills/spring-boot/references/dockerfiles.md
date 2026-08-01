# Spring Boot Dockerfiles

Open this reference when the platform requires explicit Dockerfile control.

## Multi-stage Dockerfile

```dockerfile
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /home/app
COPY mvnw mvnw
COPY .mvn .mvn
COPY pom.xml .
COPY src src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /home/app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

When the jar has layered extraction enabled, use the canonical example in [layered-jars.md](layered-jars.md).

## Validation rule

Verify the Dockerfile entrypoint, working directory, and artifact copy path match the actual build output.
