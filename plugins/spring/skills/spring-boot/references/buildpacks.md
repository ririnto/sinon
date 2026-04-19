# Spring Boot buildpacks

Open this reference when the application should produce an OCI image without a Dockerfile.

```bash
./mvnw spring-boot:build-image
```

```bash
./gradlew bootBuildImage
```

Use buildpacks when you want Spring Boot to produce an image without maintaining a Dockerfile.

## Gotchas

- Do not switch to buildpacks if the platform requires a heavily customized image layout.
