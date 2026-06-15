# Spring Boot Jackson configuration

Open this reference when the blocker is Jackson multi-format features, factory constraints, HandlerInstantiator wiring, or mapper customizer setup.

## Multi-format read/write properties

Common features across Jackson formats (JSON, CBOR, XML) are configurable via `spring.jackson.read.*` and `spring.jackson.write.*`.

```yaml
spring:
  jackson:
    read:
      unknown-properties: false
      fail-on-unknown-properties: false
    write:
      empty-collections: false
      write-date-timestamps-as-nanoseconds: false
```

## Factory configuration

Factory-level read/write constraints use `spring.jackson.factory.*`.

```yaml
spring:
  jackson:
    factory:
      stream-read-constraints:
        max-string-length: "256KB"
        max-number-length: "1024"
      stream-write-constraints:
        max-nesting-depth: 50
```

## HandlerInstantiator

Auto-configured mappers use a `HandlerInstantiator` that resolves handler instances (serializers, deserializers, etc.) from application context beans.
Register custom handlers as beans and they are discovered automatically.

```java
@Configuration(proxyBeanMethods = false)
class MyJacksonConfiguration {
    @Bean
    MyCustomHandler myCustomHandler() {
        return new MyCustomHandler();
    }
}
```

## Builder customizer beans

For advanced customization, register ordered customizer beans.
Boot's own customizer has order 0.

```java
@Configuration(proxyBeanMethods = false)
class MyJacksonCustomizerConfiguration {
    @Bean
    JsonMapperBuilderCustomizer myCustomizer() {
        return (builder) -> builder.configure(SerializationFeature.INDENT_OUTPUT, true);
    }
    @Bean
    JsonFactoryBuilderCustomizer myFactoryCustomizer() {
        return (builder) -> builder.streamReadConstraints(
            StreamReadConstraints.builder().maxNameLength(128).build()
        );
    }
}
```

Available customizer types:

- `JsonMapperBuilderCustomizer` for `JsonMapper.Builder`
- `JsonFactoryBuilderCustomizer` for `JsonFactoryBuilder`
- `CborFactoryBuilderCustomizer` for `CBORFactoryBuilder`
- `XmlFactoryBuilderCustomizer` for `XmlFactoryBuilder`

## Jackson 2 migration defaults

When migrating from Jackson 2, set `spring.jackson.use-jackson2-defaults=true` to configure the auto-configured `JsonMapper` with defaults close to what Boot used for Jackson 2.

## Gotchas

- Do not mix Jackson 2 `ObjectMapper` customization with Jackson 3 `JsonMapper` customization.
- Use the Jackson 3 path for Spring Boot 4.x applications.
