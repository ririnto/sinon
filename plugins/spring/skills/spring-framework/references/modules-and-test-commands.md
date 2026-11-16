# Modules and test commands

## First safe commands

For wiring changes, use the narrowest existing TestContext-backed test that proves the affected integration.
The following commands illustrate test selection.
Use the target repository's native build command.

```sh
./mvnw test -Dtest=AppConfigTests
```

```sh
./gradlew test --tests AppConfigTests
```

## Module selection

Use only the Spring Framework modules the application actually needs.
The `7.0.9` BOM below illustrates the documented Framework baseline.
For a new BOM, check `org.springframework:spring-framework-bom` on Maven Central for the latest stable release compatible with the project's Boot line.
Keep an existing BOM or version-catalog pin unless the task authorizes changing it.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-framework-bom</artifactId>
            <version>7.0.9</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-context</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-tx</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Add `spring-jdbc` or other framework modules only when the task truly needs them.
