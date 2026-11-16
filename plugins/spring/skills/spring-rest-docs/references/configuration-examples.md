# Configuration and test examples

## Example 1: Dependency baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.restdocs</groupId>
        <artifactId>spring-restdocs-mockmvc</artifactId>
        <version>4.0.1</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Example 2: Non-Boot test configuration shape

```java
@ExtendWith(RestDocumentationExtension.class)
class OrderDocumentationTests {
    MockMvc mvc;

    @BeforeEach
    void setUp(WebApplicationContext context, RestDocumentationContextProvider restDocumentation) {
        this.mvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(documentationConfiguration(restDocumentation))
            .build();
    }
}
```

## Example 3: Gradle publishing shape

```kotlin
configurations {
    create("asciidoctorExt")
}

dependencies {
    "asciidoctorExt"("org.springframework.restdocs:spring-restdocs-asciidoctor")
}

val snippetsDir = file("build/generated-snippets")

tasks.test {
    outputs.dir(snippetsDir)
}

tasks.asciidoctor {
    configurations("asciidoctorExt")
    inputs.dir(snippetsDir)
    dependsOn(tasks.test)
}
```

## Example 4: Maven publishing shape

```xml
    <plugin>
        <groupId>org.asciidoctor</groupId>
        <artifactId>asciidoctor-maven-plugin</artifactId>
        <dependencies>
            <dependency>
                <groupId>org.springframework.restdocs</groupId>
                <artifactId>spring-restdocs-asciidoctor</artifactId>
                <version>4.0.1</version>
            </dependency>
        </dependencies>
        <executions>
            <execution>
                <phase>prepare-package</phase>
                <goals>
                    <goal>process-asciidoc</goal>
                </goals>
        </execution>
    </executions>
    <configuration>
        <attributes>
            <snippets>${project.build.directory}/generated-snippets</snippets>
        </attributes>
    </configuration>
</plugin>
```
