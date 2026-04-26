# Spring Boot auto-configuration diagnostics

Open this reference when the ordinary starter-and-properties path in `SKILL.md` is not enough and the task is specifically about why Boot did or did not wire a bean.

Use this branch for condition-evaluation debugging, Boot wiring tests, and configuration diagnosis, not for ordinary feature work.

## Primary diagnostics path

Start with Boot's condition evaluation report before changing code.

### Enable the report with debug logging

```properties
logging.level.org.springframework.boot.autoconfigure=DEBUG
```

Or start the application with:

```bash
java -jar app.jar --debug
```

This shows which auto-configurations matched and which ones were rejected.

## Actuator conditions endpoint

If Actuator is present, expose the conditions report and inspect it over HTTP.

```properties
management.endpoints.web.exposure.include=conditions
```

```text
GET /actuator/conditions
```

Expected response shape:

```json
{
  "contexts": {
    "application": {
      "positiveMatches": {},
      "negativeMatches": {}
    }
  }
}
```

## Condition annotations to check first

When a bean is unexpectedly missing or duplicated, inspect these built-in conditions first:

- `@ConditionalOnClass`
- `@ConditionalOnMissingClass`
- `@ConditionalOnBean`
- `@ConditionalOnMissingBean`
- `@ConditionalOnProperty`
- `@ConditionalOnResource`
- `@ConditionalOnWebApplication`
- `@ConditionalOnNotWebApplication`

### Common override-safe pattern

```java
@Configuration
@ConditionalOnClass(DataSource.class)
@ConditionalOnMissingBean(DataSource.class)
class MyDataSourceAutoConfiguration {
    @Bean
    DataSource dataSource() {
        return new MyDataSource();
    }
}
```

This shape means user-defined beans can replace the auto-configured bean cleanly.

## Configuration properties binding diagnostics

When `@ConfigurationProperties` does not bind as expected:

1. Check the prefix is correct and uses kebab case in properties.
2. Check the property source actually loads in the active profile.
3. Enable debug logging for configuration binding.

```properties
logging.level.org.springframework.boot.context.properties=DEBUG
```

1. Inspect the config properties actuator endpoint if available.

```properties
management.endpoints.web.exposure.include=configprops
```

## `ApplicationContextRunner` pattern

Use `ApplicationContextRunner` when diagnosing Boot wiring in tests without starting the full app.

```java
class MyAutoConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MyDataSourceAutoConfiguration.class));

    @Test
    void createsDataSourceWhenMissing() {
        runner.run(context -> assertThat(context).hasSingleBean(DataSource.class));
    }
}
```

## Decision points

| Symptom | First check |
| --- | --- |
| Bean not created | `negativeMatches` in the condition report |
| Too many beans | user bean vs auto-config bean precedence |
| Property ignored | prefix, profile, and binding logs |
| Web bean missing | servlet vs reactive application type |
| Custom auto-config not loaded | imports registration and package visibility |

## Output contract

Return:

1. The failing auto-configuration class or bean name.
2. The exact rejected condition or missing input.
3. The minimal configuration or dependency change needed to make it match.
