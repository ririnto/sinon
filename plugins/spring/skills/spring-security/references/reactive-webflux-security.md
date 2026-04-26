# Spring Security reactive WebFlux security

Open this reference when the task is for a reactive WebFlux application that uses `SecurityWebFilterChain` and the servlet path in `SKILL.md` no longer applies.

Use reactive security only when the application is actually reactive end to end.

- Good fit: `SecurityWebFilterChain` in a WebFlux application.
- Poor fit: servlet applications trying to borrow reactive patterns.

## Minimal reactive configuration

Use `ServerHttpSecurity`, not `HttpSecurity`, in reactive applications.

The `{noop}` password encoder marker is for local smoke tests only.

```java
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
class WebFluxSecurityConfig {
    @Bean
    ReactiveUserDetailsService reactiveUserDetailsService() {
        UserDetails user = User.withUsername("user")
            .password("{noop}password")
            .roles("USER")
            .build();
        return new MapReactiveUserDetailsService(user);
    }

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/health").permitAll()
                .anyExchange().authenticated())
            .httpBasic(Customizer.withDefaults())
            .formLogin(Customizer.withDefaults())
            .build();
    }
}
```

If the real application authenticates through another reactive mechanism, replace the sample `ReactiveUserDetailsService` bean with that production authentication source.

## Multiple filter chains by path

Use a dedicated chain when API paths and browser paths need different security behavior.

```java
@Bean
@Order(Ordered.HIGHEST_PRECEDENCE)
SecurityWebFilterChain apiChain(ServerHttpSecurity http) {
    return http
        .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/**"))
        .authorizeExchange(exchanges -> exchanges.anyExchange().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .build();
}

@Bean
SecurityWebFilterChain appChain(ServerHttpSecurity http) {
    return http
        .authorizeExchange(exchanges -> exchanges
            .pathMatchers("/login", "/assets/**").permitAll()
            .anyExchange().authenticated())
        .formLogin(Customizer.withDefaults())
        .build();
}
```

When one reactive chain narrows itself with `securityMatcher(...)`, keep an explicit fallback chain for the rest of the exchanges.

## Reactive resource server shapes

### JWT

```java
.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
```

### Opaque token

```java
.oauth2ResourceServer(oauth2 -> oauth2.opaqueToken(Customizer.withDefaults()))
```

Choose JWT when the issuer provides self-contained tokens. Use opaque token support when introspection is the real contract.

## Reactive testing with `WebTestClient`

```java
@WebFluxTest(SecureController.class)
@Import(WebFluxSecurityConfig.class)
class SecureControllerTests {
    @Autowired
    WebTestClient webTestClient;

    @Test
    void unauthenticatedRequestIsRejected() {
        webTestClient.get().uri("/secure")
            .exchange()
            .expectStatus().isUnauthorized();
    }
}
```

Use the reactive security test support when the request needs a mocked JWT or authentication context.

## Decision points

| Situation | Use |
| --- | --- |
| Reactive app with browser login | `SecurityWebFilterChain` + `formLogin` |
| Reactive API with bearer tokens | `SecurityWebFilterChain` + resource server |
| Mixed servlet and reactive stack | split by application type, do not merge configs |
