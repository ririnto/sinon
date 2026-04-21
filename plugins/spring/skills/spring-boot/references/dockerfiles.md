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

## Layered-jar Dockerfile

When the jar has layered extraction enabled:

```dockerfile
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /builder
COPY target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre
WORKDIR /application
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./
ENTRYPOINT ["java", "-jar", "application.jar"]
```

## Validation rule

Verify the Dockerfile entrypoint, working directory, and artifact copy path match the actual build output.
