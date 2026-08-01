# Spring Boot layered jars

Open this reference when container rebuild speed depends on jar layers.

The `layertools` jar mode was removed in 4.1.0.
Use `tools` jar mode (`java -Djarmode=tools -jar app.jar`) which provides the same features.

## Inspect layers

```sh
java -Djarmode=tools -jar app.jar list-layers
```

## Extract layers

```sh
java -Djarmode=tools -jar app.jar extract --layers --destination extracted
```

## Canonical Dockerfile with layered jars

Use this as the single layered-jar Dockerfile example for the skill.

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

## Enable layered jars in Maven

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <layers>
                    <enabled>true</enabled>
                </layers>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Validation rule

Verify the produced jar actually contains the intended layer structure before changing the image pipeline.
