# Session configuration

## Redis-backed application properties

For redis-backed application properties, see [example 1](configuration-examples.md#example-1-redis-backed-application-properties).

## Cookie customization and indexed repository shape

Use the indexed Redis variant when principal lookup is part of the job.

For cookie customization and indexed repository shape, see [example 2](configuration-examples.md#example-2-cookie-customization-and-indexed-repository-shape).

The Redis session starter and `repository-type: indexed` select `RedisIndexedSessionRepository`.
Do not add an enable annotation to the Boot-managed common path.

## Header-based session id shape

Use this only when a non-browser client contract requires header transport.

```java
@Bean
HttpSessionIdResolver httpSessionIdResolver() {
    return HeaderHttpSessionIdResolver.xAuthToken();
}
```

## Serialization rule

The default Redis serializer is acceptable for one application controlling both reads and writes.
Switch to JSON serialization when multiple applications, rolling upgrades, or payload inspection requirements make default serialization too brittle.
