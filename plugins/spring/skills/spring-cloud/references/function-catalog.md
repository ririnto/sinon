# Spring Cloud Function catalog

Open this reference when the ordinary config-gateway-client path in `SKILL.md` is not enough and the task is specifically about Spring Cloud Function beans or function composition.

```java
@Bean
Function<String, String> uppercase() {
    return value -> value.toUpperCase();
}

@Bean
Supplier<String> source() {
    return () -> "hello";
}

@Bean
Consumer<String> sink() {
    return value -> {
    };
}
```

```yaml
spring:
  cloud:
    function:
      definition: uppercase|reverse
```

Use composition when the flow is still one logical function chain.

## Gotchas

- Do not treat a reusable in-process function catalog as a broker-driven Stream topology.
- Do not compose functions when separate deployment boundaries are the real need.

## Validation rule

Verify the selected function definition resolves to the expected bean chain at runtime.
