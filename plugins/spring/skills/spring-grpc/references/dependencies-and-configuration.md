# Dependencies and configuration

## Dependency baseline

Use only the starter set the application actually needs on the compatible Spring gRPC line.
The `4.1.1` Boot BOM below illustrates the documented Spring gRPC pairing.
For a new BOM, check `org.springframework.boot:spring-boot-dependencies` on Maven Central for the latest stable release compatible with Spring gRPC.
Keep the existing Boot BOM or version-catalog pin unless the task authorizes changing it.

### Stable BOM baseline

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>4.1.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Boot starters are versioned by the Spring Boot BOM.
Keep starter coordinates versionless underneath it.

### Server-only baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-grpc-server</artifactId>
    </dependency>
</dependencies>
```

### Client-only baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-grpc-client</artifactId>
    </dependency>
</dependencies>
```

### Combined application baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-grpc-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-grpc-client</artifactId>
    </dependency>
</dependencies>
```

The Boot server starter already includes `io.grpc:grpc-services` for reflection and health support.
Add that artifact explicitly only in a manual runtime that does not use `spring-boot-starter-grpc-server`.

### Proto generation baseline

Generate Java types from `.proto` files before implementing services or clients.
With `spring-boot-starter-parent` 4.1, use Boot's managed `io.github.ascopes:protobuf-maven-plugin` entry and let Boot configure `protoc`, the binary plugin, and the `generate` goal.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.ascopes</groupId>
            <artifactId>protobuf-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

Place application `.proto` files under `src/main/proto`.
If the project does not use `spring-boot-starter-parent`, configure the plugin directly from the `io.github.ascopes:protobuf-maven-plugin` documentation and prefer Spring Boot's managed `${protobuf-java.version}` and `${grpc-java.version}` properties.
Check generated sources into the ordinary build output, not into hand-maintained source folders.
If the project intentionally tracks generated sources in VCS, keep the `.proto` contract and generated stubs in the same reviewed change.

### Milestone branch note

Spring gRPC 1.1 moves starters and autoconfiguration into Spring Boot 4.1.
Use the Boot-managed gRPC starters on this line.

## First safe configuration

### First safe commands

```sh
./mvnw test -Dtest=GreeterServiceIntegrationTests
```

```sh
./gradlew test --tests GreeterServiceIntegrationTests
```

### Server properties

```yaml
spring:
  grpc:
    server:
      port: 9090
```

### Client properties

```yaml
spring:
  grpc:
    client:
      channel:
        greeter:
          target: static://localhost:9090
```

Start with explicit static addresses in local development.
Add service discovery or advanced channel customization only when the deployment actually needs it.
