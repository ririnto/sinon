# Spring Boot native image

Open this reference when the blocker is native-image build or runtime behavior.

```bash
./mvnw -Pnative native:compile
```

```bash
./mvnw spring-boot:build-image -Dspring-boot.build-image.environment.BP_NATIVE_IMAGE=true
```

Keep ordinary tests JVM-first. Add native-specific verification only when the deployment target actually uses native images.

## Gotchas

- Do not adopt native-image as the default build path without a deployment reason.
