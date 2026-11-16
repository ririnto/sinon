# Configuration and test examples

## Example 1: Dependency baseline

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.1</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Example 2: Minimal application shape

```java
/**
 * Entry point that boots the application through Spring Boot's auto-configuration.
 */
@SpringBootApplication
public class Application {
    /**
     * Launches the Spring application context.
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## Example 3: Configuration properties shape

```java
@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
class CatalogConfiguration {
}

/**
 * Holds the validated {@code catalog} configuration properties.
 */
@Validated
@ConfigurationProperties("catalog")
public record CatalogProperties(@NotBlank String region, int pageSize) {
}
```

## Example 4: Test strategy baseline

```java
@RestController
@RequestMapping("/api/greetings")
class GreetingController {
    @GetMapping
    Map<String, String> greet(@RequestParam(defaultValue = "world") String name) {
        return Map.of("message", "Hello " + name);
    }
}

@WebMvcTest(GreetingController.class)
class GreetingControllerTests {
    @Autowired
    MockMvc mvc;

    @Test
    void greeting() throws Exception {
        mvc.perform(get("/api/greetings").param("name", "Spring"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Hello Spring"));
    }
}
```
