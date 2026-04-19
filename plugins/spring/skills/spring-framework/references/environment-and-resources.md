# Spring Framework Environment, Profiles, and Resources

Open this reference when the common path in `SKILL.md` is not enough and the task needs deeper control over profiles, property sources, or resource resolution.

## Profile-driven bean activation

Use `@Profile` to restrict bean or configuration class activation to specific environments:

```java
@Configuration
@Profile("production")
class ProductionDataConfig {
    @Bean
    DataSource dataSource() {
        return new HikariDataSource();
    }
}
```

Activate multiple profiles in tests:

```java
@ActiveProfiles({"dev", "h2"})
```

Set active profiles programmatically:

```java
ctx.getEnvironment().setActiveProfiles("dev");
```

Use profiles to swap implementation beans for different environments without changing the application wiring shape.

## Property source ordering

Spring resolves property sources in order. Earlier sources take precedence over later ones.

```java
ctx.getEnvironment().getPropertySources()
    .addLast(new ResourcePropertySource("file:prod.properties"));
```

Common plain-Spring sources include, from higher to lower precedence:

1. Custom sources added with `addFirst(..)`
2. JVM system properties
3. OS environment variables
4. Sources added later with `addLast(..)` or `@PropertySource`

Use `@PropertySource` to add application-level property files. Use `PropertySourcesPlaceholderConfigurer` or a custom `PropertySource` when placeholder resolution or source ordering needs more control.

## Externalized configuration with `@Value`

Inject property values directly:

```java
@Value("${app.inventory.max-items:100}")
int maxItems;
```

Use SpEL for derived values:

```java
@Value("#{environment['app.base-url']}/api/${app.version}")
String apiBase;
```

## Resource resolution

| Prefix | Resolution |
| --- | --- |
| `classpath:` | classpath root |
| `file:` | filesystem path |
| `https:` / other URL schemes | resource resolved through the standard URL loader |

```java
Resource json = ctx.getResource("classpath:data/items.json");
Resource file = ctx.getResource("file:///tmp/export.csv");
```

## ClassPathResource vs FileSystemResource

Use `ClassPathResource` when the file is bundled in the JAR or classpath:

```java
Resource resource = new ClassPathResource("data.json");
```

Use `FileSystemResource` when the file is on the filesystem and must be accessed by absolute path:

```java
Resource resource = new FileSystemResource("/var/data/config.json");
```

## Resource patterns with PathMatchingResourcePatternResolver

```java
PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
Resource[] resources = resolver.getResources("classpath:data/*.json");
```

Use this when the application needs to load multiple resources matching a glob pattern.

## Custom property sources

Add a custom property source:

```java
MutablePropertySources sources = ctx.getEnvironment().getPropertySources();
sources.addFirst(new MyCustomPropertySource());
```

Implement `PropertySource` for non-standard configuration sources such as a database table, external service, or encrypted store.

## Decision points

| Situation | Use |
| --- | --- |
| Swap beans by environment | `@Profile` |
| Add external property files | `@PropertySource` |
| Inject individual values | `@Value` with SpEL |
| Load files from classpath | `classpath:` resource |
| Load files from filesystem | `file:` resource |
| Load multiple matching files | `PathMatchingResourcePatternResolver` |
| Non-standard configuration source | custom `PropertySource` |
