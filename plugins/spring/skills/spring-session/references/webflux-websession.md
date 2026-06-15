# WebFlux WebSession integration

Open reference when the application is reactive and uses `WebSession` instead of servlet `HttpSession`.

Keep the reactive runtime separate from the servlet runtime.
Do not mix `HttpSession` terminology or servlet-only configuration into a `WebSession` path.

## Reactive baseline

```java
@Configuration
@EnableRedisWebSession
class SessionConfig {
    @Bean
    LettuceConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }
}
```

Application properties:

```yaml
spring:
  session:
    store-type: redis
    timeout: 30m
    redis:
      namespace: app:session
  data:
    redis:
      host: localhost
      port: 6379
server:
  reactive:
    session:
      timeout: 30m
```

## Reactive session repository customization

Use `ReactiveSessionRepositoryCustomizer` to adjust timeout, namespace, and other reactive repository properties:

```java
@Bean
ReactiveSessionRepositoryCustomizer<ReactiveRedisSessionRepository> sessionRepositoryCustomizer() {
    return repository -> {
        repository.setDefaultMaxInactiveInterval(Duration.ofMinutes(30));
        repository.setNamespace("app:session");
    };
}
```

## Reactive security context shape

```java
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {
    @Bean
    SecurityWebFilterChain webFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchange -> exchange.anyExchange().authenticated())
            .formLogin(Customizer.withDefaults())
            .build();
    }
}
```

## Explicit WebSession save

Save the `WebSession` explicitly when the framework does not flush attribute mutations automatically:

```java
Mono<Void> handler(ServerWebExchange exchange) {
    WebSession session = exchange.getSession().block();
    session.getAttributes().put("key", "value");
    return session.save();
}
```

## Test shape

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebFluxSessionFlowTest {
    @Autowired
    WebTestClient webTestClient;

    @Test
    void sessionStateIsReusedAcrossRequests() {
        String sessionId = webTestClient.post().uri("/cart/items")
            .bodyValue(Map.of("sku", "SKU-1"))
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseCookies()
            .getFirst("SESSION")
            .getValue();
        webTestClient.post().uri("/cart/items")
            .bodyValue(Map.of("sku", "SKU-2"))
            .cookie("SESSION", sessionId)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.itemCount").isEqualTo(2);
    }
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Reactive app needs shared login state across nodes | `@EnableRedisWebSession` |
| Session mutation timing is ambiguous in a low-level flow | save the `WebSession` explicitly |
| Reactive security context must survive across nodes | validate `WebSession` persistence together with security behavior |
| Reactive Redis session timeout or namespace needs adjustment | `ReactiveSessionRepositoryCustomizer` |

## Gotchas

- Do not reuse servlet `HttpSessionIdResolver` or servlet cookie APIs in a reactive path.
- Do not assume a servlet-only logout or listener pattern applies to `WebSession`.
- Do not block inside session serialization or persistence hooks.
- Do not use `@EnableRedisHttpSession` or `@EnableRedisIndexedHttpSession` in a reactive application.
  - Use `@EnableRedisWebSession` exclusively.
