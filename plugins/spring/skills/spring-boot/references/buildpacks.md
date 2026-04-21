# Spring Boot buildpacks

Open this reference when the application should produce an OCI image without a Dockerfile.

## Build with Maven

```bash
./mvnw spring-boot:build-image
```

## Build with Gradle

```bash
./gradlew bootBuildImage
```

## Image naming

Default image name follows `docker.io/library/{artifact-name}:{version}`. Override with:

```bash
./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=myregistry/myapp:latest
```

## Gotchas

- Do not switch to buildpacks if the platform requires a heavily customized image layout.
- Set the image registry before the first production push; default Docker Hub may not match your infrastructure.
