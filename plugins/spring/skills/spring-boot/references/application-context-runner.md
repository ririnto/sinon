# Spring Boot `ApplicationContextRunner`

Open this reference when the ordinary test-slice baseline in [SKILL.md](../SKILL.md) is not enough and the blocker is Boot-specific wiring diagnosis without starting the whole application.

## `ApplicationContextRunner` shape

```java
class MyAutoConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withPropertyValues("catalog.region=eu-west-1");

    @Test
    void loadsCatalogProperties() {
        runner.run(context -> assertThat(context).hasSingleBean(CatalogProperties.class));
    }
}
```

## Gotchas

- Do not escalate to `@SpringBootTest` when the blocker is only conditional wiring.
- Do not mix unrelated environment setup into a context-runner test.
