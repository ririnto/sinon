# Spring Session WebFlux WebSession

Open this reference when the application is reactive and uses `WebSession` instead of servlet `HttpSession`.

Keep the reactive runtime separate from the servlet runtime. Do not mix `HttpSession` terminology or servlet-only configuration into a `WebSession` path.

## Reactive baseline

```java
@Configuration
@EnableRedisWebSession
class SessionConfig {
}
```

## Reactive security context shape

```java
@Bean
SecurityWebFilterChain webFilterChain(ServerHttpSecurity http) {
    return http
        .authorizeExchange(exchange -> exchange.anyExchange().authenticated())
        .formLogin(Customizer.withDefaults())
        .build();
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Reactive app needs shared login state across nodes | `@EnableRedisWebSession` |
| Session mutation timing is ambiguous in a low-level flow | save the `WebSession` explicitly |
| Reactive security context must survive across nodes | validate `WebSession` persistence together with security behavior |

## Gotchas

- Do not reuse servlet `HttpSessionIdResolver` or servlet cookie APIs in a reactive path.
- Do not assume a servlet-only logout or listener pattern applies to `WebSession`.
- Do not block inside session serialization or persistence hooks.
