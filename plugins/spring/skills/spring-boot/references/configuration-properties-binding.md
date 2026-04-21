# Spring Boot configuration-properties binding

Open this reference when `@ConfigurationProperties` binding behavior is the blocker.

If binding fails, check relaxed-name mapping, prefix spelling, active profiles, imported config, and validation rules before changing the code model.

```java
@ConfigurationProperties("catalog")
public record CatalogProperties(URI serviceUrl, Duration timeout) {
}
```

```yaml
catalog:
  service-url: https://example.internal
  timeout: 5s
```

## Gotchas

- Do not blame the record or class shape before checking the effective property names.
