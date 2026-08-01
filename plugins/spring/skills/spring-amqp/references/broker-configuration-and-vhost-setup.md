# Broker configuration and vhost setup

Open this reference when the common-path topology declarations are not enough and the blocker is broker credentials, virtual-host selection, or explicit connection settings.

## Broker configuration blocker

Problem: the application needs explicit broker credentials, virtual-host selection, or custom connection settings before messaging can work predictably.

Solution: keep broker connection properties explicit and environment-backed.

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: app
    password: ${RABBITMQ_PASSWORD}
    virtual-host: /orders
```

## Pitfalls

- Do not rely on ambient local defaults once the service is shared across environments.
- Keep connection settings reviewable alongside queue and exchange contracts.
- When specifying `spring.rabbitmq.addresses` with an `amqps://` URI, SSL is enabled automatically; the separate `spring.rabbitmq.ssl.enabled` property is not needed in that case.
