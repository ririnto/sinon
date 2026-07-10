# Spring Boot war packaging

Open this reference when a traditional servlet container is a hard requirement.

## Enable war packaging in pom.xml

```xml
<packaging>war</packaging>
```

## Extend SpringBootServletInitializer

```java
@SpringBootApplication
public class Application extends SpringBootServletInitializer {
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Application.class);
    }
}
```

Apply this to the existing `@SpringBootApplication` class when the application must remain deployable as a traditional WAR.

## Mark the embedded container as provided

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-tomcat</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

## Gotchas

- Do not keep war packaging just because it was historically used if the platform now supports executable jars.
- If the deployment target supports executable jars and OCI images, prefer those over war packaging.
