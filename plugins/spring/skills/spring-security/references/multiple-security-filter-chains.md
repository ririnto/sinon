# Spring Security multiple servlet filter chains

Open this reference when one servlet `SecurityFilterChain` is no longer enough because different URL spaces need different authentication styles or a custom filter must be placed relative to Spring Security's built-in filters.

Use separate servlet chains when the matching rules, authentication mechanism, or session policy differ by path.

## Multiple `SecurityFilterChain` beans

Keep the more specific matcher on the higher-priority bean.

```java
@Configuration
class SecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }

    @Bean
    SecurityFilterChain web(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth.requestMatchers("/login", "/assets/**").permitAll().anyRequest().authenticated())
            .formLogin(Customizer.withDefaults())
            .build();
    }
}
```

## Custom filter placement

Use Spring Security's filter-chain methods instead of relying on raw servlet-container filter ordering.

```java
@Bean
SecurityFilterChain api(HttpSecurity http, RequestIdFilter requestIdFilter) throws Exception {
    return http
        .securityMatcher("/api/**")
        .addFilterBefore(requestIdFilter, BearerTokenAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .build();
}
```

## Decision points

| Situation | Use |
| --- | --- |
| API and browser pages need different auth styles | multiple `SecurityFilterChain` beans |
| A custom filter must see the request before bearer-token auth runs | `addFilterBefore(..., BearerTokenAuthenticationFilter.class)` |
| A custom filter enriches the request after username-password auth | `addFilterAfter(..., UsernamePasswordAuthenticationFilter.class)` |
| Two chains overlap | make the matcher explicit and give the narrower matcher the higher priority |

## Gotchas

- Do not rely on `@Order` alone without a clear `securityMatcher`.
- Do not place custom filters with container-wide registration when they are part of a security decision.
- Do not mix stateful browser login and stateless API policy in one chain unless the URLs truly share the same behavior.
