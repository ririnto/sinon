---
title: Spring Security Configuration Recipes
description: >-
  Additive configuration recipes for Spring Security: multiple filter chains and CORS/CSRF operational notes.
---

Use this reference when the access model is clear and the remaining work is implementing specific configuration patterns not covered in the main skill.

## Multiple Filter Chains Recipe

Use multiple chains only when different request surfaces genuinely need different security models.
If you split chains, keep one explicit default chain for every request not matched earlier. Do not assume unmatched routes stay protected automatically.

```java
@Bean
@Order(1)
SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
    return http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .build();
}

@Bean
@Order(2)
SecurityFilterChain appChain(HttpSecurity http) throws Exception {
    return http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/logout", "/assets/**").permitAll()
                    .anyRequest().authenticated())
            .build();
}
```

## CORS Configuration Recipe

Always configure CORS explicitly rather than relying on defaults or allowing all origins:

```java
@Bean
SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
    return http
            .securityMatcher("/api/**")
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .build();
}

@Bean
CorsConfigurationSource corsConfigurationSource() {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("https://*.example.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    source.registerCorsConfiguration("/api/**", config);
    return source;
}
```

Do not use `setAllowedOrigins("*")` when credentials or `Authorization` headers are needed; use `allowedOriginPatterns` instead.

## CSRF and Session vs Stateless API Decision Aid

| API type | CSRF risk | Recommended approach |
| --- | --- | --- |
| browser session app with form login | high | keep CSRF enabled; use `CsrfToken` in forms or `CookieCsrfTokenRepository` |
| stateless REST API with `Authorization: Bearer` | low | disable CSRF; `csrf().disable()` on the stateless filter chain |
| SPA with session cookies | medium | consider `CookieCsrfTokenRepository` or double-submit cookie pattern |
| mobile app or service-to-service | low | disable CSRF; no browser cookie context |

```java
// Stateless JWT API chain
@Bean
@Order(1)
SecurityFilterChain statelessApiChain(HttpSecurity http) throws Exception {
    return http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
}

// Browser session chain with CSRF enabled
@Bean
@Order(2)
SecurityFilterChain appChain(HttpSecurity http) throws Exception {
    return http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/logout", "/assets/**").permitAll()
                    .anyRequest().authenticated())
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers("/api/**"))  // only ignore for stateless API sub-path
            .build();
}
```

Error-contract shape, method-security boundaries, and matcher rules should stay in the active security guidance instead of requiring a second reference hop.
