# Spring Boot native image

Open this reference when the blocker is native-image build or runtime behavior.

## Build with Maven

```bash
./mvnw -Pnative native:compile
```

## Build with Boot build-image

```bash
./mvnw spring-boot:build-image -Dspring-boot.build-image.environment.BP_NATIVE_IMAGE=true
```

## GraalVM configuration

When static GraalVM metadata is unavoidable, place it under `src/main/resources/META-INF/native-image/<groupId>/<artifactId>/reflect-config.json` so it stays scoped to the application coordinates.

## Test strategy

Keep ordinary tests JVM-first. Add native-specific verification only when the deployment target actually uses native images.

## Gotchas

- Do not adopt native-image as the default build path without a deployment reason.
- Verify the native binary starts and passes basic smoke tests before deploying, as runtime behavior can differ from JVM runs.
