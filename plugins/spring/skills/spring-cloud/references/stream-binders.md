# Spring Cloud Stream binders

Open this reference when the ordinary config-gateway-client path in `SKILL.md` is not enough and the task is specifically about Spring Cloud Stream binder wiring.

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-kafka</artifactId>
</dependency>
```

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-rabbit</artifactId>
</dependency>
```

Choose the binder that matches the real broker.

## Gotchas

- Do not mix binder-specific behavior into a generic service contract casually.
- Do not add several binders unless the application truly speaks to several brokers.

## Validation rule

Verify the chosen binder and destination names match the real broker and topic or queue contract.
