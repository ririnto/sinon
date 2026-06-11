# Spring Boot buildpacks

Open this reference when the application should produce an OCI image without a Dockerfile.

## Build with Maven

```sh
./mvnw spring-boot:build-image
```

## Build with Gradle

```sh
./gradlew bootBuildImage
```

## Gradle CLI environment variables

Pass environment variables to the build image task from the command line. Multiple `--environment` flags are supported. CLI values take precedence over build script values.

```sh
./gradlew bootBuildImage --environment BP_JVM_VERSION=21 --environment BPE_APP_PORT=8080
```

## Image naming

Default image name follows `docker.io/library/{artifact-name}:{version}`. Override with:

```sh
./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=myregistry/myapp:latest
```

## BuildInfo Gradle task

The `BuildInfo` task now outputs to `META-INF/build-info.properties` by default. Customize with the `filename` property.

```kotlin
tasks.named<org.springframework.boot.gradle.tasks.buildinfo.BuildInfo>("buildInfo") {
    filename.set("build-info.properties")
}
```

## Maven layers.xml from classpath

The Maven plugin can load custom layers configuration from `META-INF/spring/layers/.xml` added as a plugin dependency.

## Gotchas

- Do not switch to buildpacks if the platform requires a heavily customized image layout.
- Set the image registry before the first production push; default Docker Hub may not match your infrastructure.
