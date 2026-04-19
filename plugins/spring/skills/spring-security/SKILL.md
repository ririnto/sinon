---
name: "spring-security"
description: "Use this skill when securing Spring applications with `SecurityFilterChain`, authentication and authorization rules, password storage, resource server JWT validation, method security, session and CSRF policy, and Spring Security tests."
metadata:
  title: "Spring Security"
  official_project_url: "https://spring.io/projects/spring-security"
  reference_doc_urls:
    - "https://docs.spring.io/spring-security/reference/index.html"
  version: "7.0.4"
---

Use this skill when securing Spring applications with `SecurityFilterChain`, authentication and authorization rules, password storage, resource server JWT validation, method security, session and CSRF policy, and Spring Security tests.

## Boundaries

Use `spring-security` for application-side authentication, authorization, filter-chain design, method security, CORS and CSRF policy, session behavior, logout behavior, security headers, exception handling, and security-focused tests.

- Keep token issuance, OAuth2 provider behavior, and OIDC provider behavior outside this skill's scope.
- Keep business rules outside security configuration. Security should express access policy and integration boundaries, not business workflows.
- Keep reactive security outside the ordinary servlet path. Open the reactive reference only when the application is reactive end to end.

## Common path

The ordinary Spring Security job is:

1. Start with one explicit `SecurityFilterChain` instead of relying on hidden defaults.
2. Define public endpoints, authenticated endpoints, and authority-based rules explicitly.
3. Choose the authentication style deliberately, such as form login, HTTP basic, or resource-server JWT.
4. Keep CORS, CSRF, and session policy explicit so browser flows and API flows do not accidentally share the wrong defaults.
5. Keep password encoding, JWT claim mapping, and principal extraction explicit.
6. Keep logout behavior, security headers, and 401 versus 403 handling explicit.
7. Add security-focused tests that prove both allowed and denied access.

### Branch selector

- Stay in `SKILL.md` for the ordinary servlet path: one main `SecurityFilterChain`, explicit authorization rules, password encoding, basic CORS and CSRF policy, session policy, logout behavior, JWT resource-server validation, method security, and allow or deny tests.
- Open [references/reactive-webflux-security.md](references/reactive-webflux-security.md) only when the application is reactive end to end and uses `SecurityWebFilterChain`.
- Open [references/delegated-login-and-oauth2-client.md](references/delegated-login-and-oauth2-client.md) when the application delegates login to an external identity provider or needs outbound OAuth2 client token management.
- Open [references/ldap-authentication.md](references/ldap-authentication.md) when directory-backed authentication is part of the job.
- Open [references/saml2-login.md](references/saml2-login.md) only when enterprise identity-provider requirements specifically depend on SAML2.
- Open [references/multiple-security-filter-chains.md](references/multiple-security-filter-chains.md) when different URL spaces need separate servlet chains or a custom filter must be placed relative to Spring Security's built-in filters.
- Open [references/jwt-claim-mapping.md](references/jwt-claim-mapping.md) when default `scope` to authority conversion is not enough or issuer, audience, algorithm, or principal-claim rules must be customized.
- Open [references/security-exception-handling.md](references/security-exception-handling.md) when 401 and 403 responses need custom JSON bodies, custom entry points, or custom access-denied handling.
- Open [references/session-management-and-logout.md](references/session-management-and-logout.md) when the job depends on concurrent-session control, custom logout success handling, or stateful session persistence rules beyond the ordinary path.
- Open [references/security-headers.md](references/security-headers.md) when defaults are not enough and the application needs custom CSP, HSTS, frame, or permissions-policy behavior.

## Dependency baseline

### Core starter and test support

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

### Optional resource-server add-on

Add the resource-server starter only when the application validates incoming bearer tokens.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

## First safe configuration

### First safe commands

```bash
./mvnw test
```

```bash
./gradlew test
```

### Stateless API baseline

Use this shape for token-based APIs that should not create login sessions.

```java
@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                .anyRequest().authenticated())
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
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
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/login", "/assets/**").permitAll()
            .anyRequest().authenticated())
        .formLogin(Customizer.withDefaults())
        .logout(logout -> logout
            .logoutUrl("/logout")
            .logoutSuccessUrl("/login?logout"))
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
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

### Inbound versus outbound OAuth2 boundary

- Use resource-server support when the application validates incoming bearer tokens.
- Use the delegated-login or OAuth2-client reference when the application signs users in through an external provider or obtains outbound access tokens for downstream APIs.

## Coding procedure

1. Keep authorization rules explicit and ordered so public and protected paths are obvious.
2. Match session policy to the client model: use stateless sessions for bearer-token APIs and a stateful session policy only when browser login is part of the design.
3. Configure CORS only for real browser-cross-origin clients, and keep allowed origins, methods, and headers explicit.
4. Keep CSRF policy explicit instead of disabling it reflexively. For stateless APIs, ignore only the paths that do not rely on browser-session cookies.
5. Use a strong `PasswordEncoder` and never store raw passwords.
6. Keep JWT authority mapping, claim interpretation, and principal extraction explicit.
7. Use method security only where request-level rules are not enough. Add `@PostAuthorize`, `@PreFilter`, or `@PostFilter` only when access depends on returned objects or filtered collections.
8. Keep logout behavior explicit for browser flows, and keep 401 versus 403 handling explicit for API flows.
9. Rely on Spring Security's default security headers unless the application has a concrete need for custom CSP, frame, or HSTS behavior.
10. Test both successful access and denial paths for representative endpoints.

## TDD loop

1. Start with one endpoint that should stay public and one endpoint that must stay protected.
2. Write one allowed-access test and one denied-access test before broadening the rule set.
3. Add method-security assertions only after request-level access rules are stable.
4. Add CORS, CSRF, session, logout, or JWT claim-mapping rules only after the basic allow or deny path is verified.
5. Add custom exception handling tests only when the contract requires a specific 401 or 403 response body.

## Implementation examples

### Method security

```java
@Service
class InvoiceService {
    @PreAuthorize("hasAuthority('SCOPE_invoices:write')")
    void approve(long invoiceId) {
    }

    @PostAuthorize("returnObject.owner().equals(authentication.name)")
    InvoiceView load(long invoiceId) {
        return new InvoiceView(invoiceId, "alice");
    }
}
```

### Collection filtering branch

```java
@PreFilter(filterTarget = "invoiceIds", value = "filterObject > 0")
void reprocess(List<Long> invoiceIds) {
}
```

### Security test

```java
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTests {
    @Autowired
    MockMvc mvc;

    @Test
    void requiresScope() throws Exception {
        mvc.perform(get("/admin")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
            .andExpect(status().isOk());
    }
}
```

### Denied-access test shape

```java
@Test
void rejectsAnonymousRequest() throws Exception {
    mvc.perform(get("/admin"))
        .andExpect(status().isUnauthorized());
}
```

### Forbidden-access test shape

```java
@Test
void rejectsInsufficientAuthority() throws Exception {
    mvc.perform(get("/admin")
            .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_read"))))
        .andExpect(status().isForbidden());
}
```

## Output and configuration shapes

### Protected scope shape

```text
SCOPE_invoices:write
```

### Public endpoint rule shape

```java
.requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
```

### JWT-enabled resource-server shape

```java
.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
```

### Stateless session shape

```java
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

### Basic logout shape

```java
.logout(logout -> logout.logoutSuccessUrl("/login?logout"))
```

## Output contract

Return:

1. The chosen servlet security shape and authentication style
2. The main authorization rules, including any method-security boundary
3. The CORS, CSRF, session, logout, and security-header decisions
4. The JWT validation and claim-mapping strategy when bearer tokens are involved
5. The test shape proving allowed and denied access
6. Any blocker that requires a reactive, delegated-login, LDAP, SAML2, multi-chain, advanced session, advanced headers, advanced JWT, or custom exception-handling branch

## Testing checklist

- Verify public endpoints remain accessible without authentication.
- Verify protected endpoints deny unauthenticated requests and reject insufficient authority with the expected 401 or 403 status.
- Verify method security enforces the intended authority, returned-object, or filtered-collection rules.
- Verify CORS, CSRF, and session policy match the real client interaction model.
- Verify logout behavior only where browser-session flows require it.
- Verify password encoding or JWT claim mapping follows the expected strategy.

## Production checklist

- Keep authorization rules explicit and reviewable.
- Never store raw passwords or weak password hashes.
- Do not disable CORS, CSRF, or session protections without a concrete client-driven reason.
- Keep JWT issuer, audience, and authority mapping aligned with the deployed identity provider.
- Keep default security headers unless there is a concrete compatibility reason to customize them.
- Make 401 versus 403 behavior explicit when API clients depend on a stable error contract.
- Treat security tests as part of the application's compatibility and safety surface.

## References

- Open [references/reactive-webflux-security.md](references/reactive-webflux-security.md) when the task is specifically about `SecurityWebFilterChain` and reactive security behavior.
- Open [references/delegated-login-and-oauth2-client.md](references/delegated-login-and-oauth2-client.md) when the task needs delegated OAuth2 login or outbound OAuth2 client support.
- Open [references/ldap-authentication.md](references/ldap-authentication.md) when the task specifically depends on LDAP-backed authentication.
- Open [references/saml2-login.md](references/saml2-login.md) when the task specifically depends on SAML2.
- Open [references/multiple-security-filter-chains.md](references/multiple-security-filter-chains.md) when path-specific chains or custom filters are required.
- Open [references/jwt-claim-mapping.md](references/jwt-claim-mapping.md) when default JWT conversion is not sufficient.
- Open [references/security-exception-handling.md](references/security-exception-handling.md) when custom 401 or 403 responses are required.
- Open [references/session-management-and-logout.md](references/session-management-and-logout.md) when concurrent-session control or advanced logout behavior is required.
- Open [references/security-headers.md](references/security-headers.md) when the application needs custom security-header behavior.
