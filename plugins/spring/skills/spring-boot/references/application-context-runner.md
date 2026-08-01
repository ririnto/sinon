# Spring Boot `ApplicationContextRunner`

Open this reference when the ordinary test-slice baseline is not enough and the blocker is Boot-specific wiring diagnosis without starting the whole application.

## `ApplicationContextRunner` shape

```java
@EnableConfigurationProperties(CatalogProperties.class)
class CatalogPropertiesConfiguration {
}

class MyAutoConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(CatalogPropertiesConfiguration.class)
        .withPropertyValues("catalog.region=eu-west-1");

    @Test
    void loadsCatalogProperties() {
        runner.run(context -> assertThat(context).hasSingleBean(CatalogProperties.class));
    }
}
```

## Gotchas

- Register each `@ConfigurationProperties` type with `@EnableConfigurationProperties`, `@ConfigurationPropertiesScan`, or an equivalent mechanism before asserting or injecting it.
- Do not escalate to `@SpringBootTest` when the blocker is only conditional wiring.
- Do not mix unrelated environment setup into a context-runner test.
