# Servlet setup and configuration

## Dependency baseline

### Core starter and test support

Spring Security BOM 7.1.1 is a documented stable baseline.
For a new standalone BOM, check `org.springframework.security:spring-security-bom` on Maven Central for the latest stable release compatible with the project's Boot and Framework lines.
Keep an existing BOM or version-catalog pin unless the task authorizes changing it.
Spring Security 7.2.0-M2 is a milestone, not a stable release.

#### Spring Boot-managed path

Use this path when the application intentionally stays on the Spring Boot-managed Spring Security line for that Boot release.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

If the application stays on a Spring Boot line that still manages Spring Security 6.x, either keep that Boot-managed 6.x path or move to a platform line that is compatible with Spring Security 7 and Spring Framework 7 before copying the Spring Security 7 APIs shown here.

#### Standalone Spring Security BOM path

Use this path when the build must target the standalone Spring Security BOM instead of the Boot-managed line.

```xml
<dependencyManagement>
    <dependencies>
        <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-bom</artifactId><version>7.1.1</version><type>pom</type><scope>import</scope></dependency>
    </dependencies>
</dependencyManagement>
```

Keep Spring Security modules versionless because the imported BOM manages their versions.
This is standard Maven dependency management, not a Spring Security feature gate.

For the ordinary servlet path shown in this skill, add the core modules explicitly under that BOM:

```xml
<dependencies>
    <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-config</artifactId></dependency>
    <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-web</artifactId></dependency>
    <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
</dependencies>
```

### Optional resource-server add-on

#### Boot-managed path

Add the Boot starter only when the Boot-managed application validates incoming bearer tokens.

```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-oauth2-resource-server</artifactId></dependency>
```

#### Standalone Spring Security BOM path

Use the direct Spring Security modules when the build follows the standalone BOM path instead of Boot starters.

```xml
<dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-oauth2-resource-server</artifactId></dependency>
```

Add JOSE support as well when the resource server validates JWTs locally.

```xml
<dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-oauth2-jose</artifactId></dependency>
```

## First safe configuration

### First safe commands

```sh
./mvnw test
```

```sh
./gradlew test
```

### PathPatternRequestMatcher

Spring Security provides `PathPatternRequestMatcher` for direct matcher construction.
Use it when a matcher instance must be explicit, for example in custom `securityMatcher(...)` logic or filter-chain composition.

```java
PathPatternRequestMatcher apiMatcher = PathPatternRequestMatcher.pathPattern("/api/**");
```

For an HTTP-method-specific matcher:

```java
PathPatternRequestMatcher itemMatcher = PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/items/{id}");
```

Use the `requestMatchers("/path/**")` DSL for ordinary rules.
Reach for `PathPatternRequestMatcher` only when you need an explicit matcher object.

### Stateless API baseline

Use this shape for token-based APIs that should not create login sessions.

```java
@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll().requestMatchers(HttpMethod.GET, "/api/public/**").permitAll().anyRequest().authenticated())
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/actuator/health"))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .build();
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthorityPrefix("SCOPE_");
        authorities.setAuthoritiesClaimName("scope");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
```

Keep `securityMatcher(...)` out of a single-chain baseline unless the application also defines an explicit fallback chain, because unmatched requests are left unprotected.

### Non-webapp resource server (Spring Boot 4.1+)

Spring Boot 4.1 auto-configures `JwtDecoder`, `OpaqueTokenIntrospector`, and the authentication converter in non-web applications (no servlet or reactive web server).
The `SecurityFilterChain` bean is not created -- the application must authenticate tokens programmatically.
Use [references/jwt-claim-mapping.md](jwt-claim-mapping.md) for authority extraction configuration in non-webapp contexts.

### SPA CSRF handling

For browser SPAs that use session cookies, use Spring Security's SPA CSRF support instead of disabling CSRF entirely.

```java
@Bean
SecurityFilterChain spa(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth.requestMatchers("/login", "/assets/**").permitAll().anyRequest().authenticated())
        .formLogin(Customizer.withDefaults())
        .csrf(csrf -> csrf.spa())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .build();
}
```

Spring Security's SPA support uses `CookieCsrfTokenRepository` under the hood.
The default cookie name is `XSRF-TOKEN` and the default request header is `X-XSRF-TOKEN`.

Keep pure bearer-token API endpoints in a separate stateless chain if they must ignore CSRF entirely.

### Session-management changes (Spring Security 6/7)

Spring Security 6 and 7 tighten session behavior.
Key changes to keep explicit:

- `SessionCreationPolicy.STATELESS` tells Spring Security not to create an `HttpSession` and not to use one to obtain the `SecurityContext`.
- `SessionCreationPolicy.IF_REQUIRED` creates a session only when authentication is needed.
- `SessionCreationPolicy.NEVER` never creates a session but uses an existing one if present.
- `SessionCreationPolicy.ALWAYS` always creates a session if one does not exist.
- Since Spring Security 6, `SessionManagementFilter` no longer reads the session on every request just to detect a newly authenticated user.
- If authentication is performed manually in a controller or service, save the resulting `SecurityContext` explicitly through the chosen `SecurityContextRepository`.

Bearer-token API:

```java
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

Browser login:

```java
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
```

### Basic CORS source for a browser-facing API

Use an explicit `CorsConfigurationSource` when a browser client calls your API from another origin.

```java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("https://app.example.com"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

### Stateful browser baseline

Use this shape for server-rendered or browser-session applications.

```java
@Bean
SecurityFilterChain browser(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth.requestMatchers("/login", "/assets/**").permitAll().anyRequest().authenticated())
        .formLogin(Customizer.withDefaults())
        .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout"))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .build();
}
```

### Password encoder shape

```java
@Bean
PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
```

`PasswordEncoderFactories.createDelegatingPasswordEncoder()` creates a delegating encoder that stores the algorithm prefix inside the stored hash, supporting migration from older encoders.

For applications that need a specific hash without the delegating wrapper, use the current documented Spring Security defaults directly:

```java
@Bean
PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
}
```

Use the documented defaults when selecting a concrete encoder directly.
`BCryptPasswordEncoder` and `Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()` are also valid concrete encoder choices when the storage and migration policy intentionally selects those algorithms.
When migrating from weaker hashes, store the old encoder prefix alongside hashes and delegate to the new encoder.

Keep the OAuth2 and OIDC branch explicit.
Open [references/delegated-login-and-oauth2-client.md](delegated-login-and-oauth2-client.md) when the application delegates browser login to an external IdP or manages outbound OAuth2 client tokens for downstream calls.
