# Spring Boot configuration-properties binding

Open this reference when `@ConfigurationProperties` binding behavior is the blocker.

If binding fails, check relaxed-name mapping, prefix spelling, active profiles, imported config, and validation rules before changing the code model.

```java
@ConfigurationProperties("catalog")
/**
 * Holds the {@code catalog} configuration properties bound through relaxed-name mapping.
 */
public record CatalogProperties(URI serviceUrl, Duration timeout) {
}
```

```yaml
catalog:
  service-url: https://example.internal
  timeout: 5s
```

## Kotlin constructor binding

```kotlin
@ConfigurationProperties("catalog")
@Validated
/**
 * Holds the validated {@code catalog} configuration properties for the catalog service.
 */
data class CatalogProperties(
    @field:NotBlank
    val region: String,
    @field:NotNull
    val serviceUrl: URI
)
```

```kotlin
@SpringBootApplication
@ConfigurationPropertiesScan
/**
 * Entry point that registers the application and enables configuration property scanning.
 */
class Application

/**
 * Launches the Spring application context.
 */
fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
```

```yaml
catalog:
  region: eu-west-1
  service-url: https://catalog.internal
```

A single parameterized constructor implies constructor binding, so `@ConstructorBinding` is unnecessary here.
Constructor binding requires registration through `@EnableConfigurationProperties` or `@ConfigurationPropertiesScan`; a constructor-bound class must not be created through `@Component`, `@Bean`, or `@Import`.
Keep every bound constructor parameter immutable (`val`), non-null, and without a Kotlin default value or `@DefaultValue`; the intended values live in the paired `application.yaml`.
A missing property is not a general Kotlin non-nullity guarantee: constructor binding can supply `null` for an absent reference value, so pair `@Validated` with a constraint annotation on each required parameter; a violated constraint fails bean creation at startup.
An empty `@DefaultValue` can instead request a non-null nested object, while a Kotlin default value or value-bearing `@DefaultValue` supplies a value when the property is absent.
Validation requires a Jakarta Validation implementation on the classpath, which the `spring-boot-starter-validation` starter provides.
Constructor binding requires the `-parameters` compile flag, which the Spring Boot Gradle plugin and the Maven `spring-boot-starter-parent` apply automatically.
Bean factories compose beans; they do not carry configuration values, so keep literal settings in `application.yaml` instead of constructor arguments of `@Bean` methods.
Kotlin string templates treat `$` as an expression start, so a `@Value` placeholder needs escaping as `@Value("\${catalog.region}")`; constructor-bound properties avoid the escaping because the binder maps keys to parameters directly.

## Gotchas

- Do not blame the record or class shape before checking the effective property names.
- Do not rely on Kotlin nullability alone to reject an absent reference property; use a validation constraint instead.
