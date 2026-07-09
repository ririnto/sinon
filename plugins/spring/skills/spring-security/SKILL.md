---
name: spring-security
description: >-
  Secure Spring applications with `SecurityFilterChain`, authentication and authorization rules, bearer-token resource servers, method security, session and CSRF policy, and Spring Security tests.
  Use when configuring password storage, JWT verification, opaque-token introspection, CORS policy, security headers, or logout behavior in a servlet or reactive Spring application.
  Also covers OAuth 2.1 and OpenID Connect authorization server setup with Spring Authorization Server including registered clients, PKCE authorization code, token issuance, JWK exposure, consent, PAR, device authorization, introspection, and revocation.
---

# Spring Security

## Boundaries

Use `spring-security` for application-side authentication, authorization, filter-chain design, method security, CORS and CSRF policy, session behavior, logout behavior, security headers, exception handling, and security-focused tests.

Use the Authorization Server section when the application acts as an OAuth 2.1 or OIDC provider that issues tokens for other applications.

- Keep business rules outside security configuration.
  - Security should express access policy and integration boundaries, not business workflows.
- Keep reactive security outside the ordinary servlet path.
  - Open the reactive reference only when the application is reactive end to end.

### Resource server versus authorization server

- When the application validates bearer tokens issued by someone else, use the resource-server path (JWT or opaque-token introspection).
- When the application issues bearer tokens for other applications to consume, use the Authorization Server section.

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
- Stay in the **OAuth 2.1 / OpenID Connect Authorization Server** section below when the application issues tokens as an OAuth2 or OIDC provider.
- Open [references/reactive-webflux-security.md](references/reactive-webflux-security.md) only when the application is reactive end to end and uses `SecurityWebFilterChain`.
- Open [references/delegated-login-and-oauth2-client.md](references/delegated-login-and-oauth2-client.md) when the application delegates login to an external identity provider or needs outbound OAuth2 client token management.
- Open [references/ldap-authentication.md](references/ldap-authentication.md) when directory-backed authentication is part of the job.
- Open [references/saml2-login.md](references/saml2-login.md) only when enterprise identity-provider requirements specifically depend on SAML2.
- Open [references/multiple-security-filter-chains.md](references/multiple-security-filter-chains.md) when different URL spaces need separate servlet chains or a custom filter must be placed relative to Spring Security's built-in filters.
- Open [references/jwt-claim-mapping.md](references/jwt-claim-mapping.md) when default `scope` to authority conversion is not enough, nested or multi-location claim extraction is needed, or issuer, audience, algorithm, or principal-claim rules must be customized.
- Open [references/security-exception-handling.md](references/security-exception-handling.md) when 401 and 403 responses need custom JSON bodies, custom entry points, or custom access-denied handling.
- Open [references/session-management-and-logout.md](references/session-management-and-logout.md) when the job depends on concurrent-session control, custom logout success handling, or stateful session persistence rules beyond the ordinary path.
- Open [references/security-headers.md](references/security-headers.md) when defaults are not enough and the application needs custom CSP, HSTS, frame, or permissions-policy behavior.
- Open [references/servlet-opaque-token-resource-server.md](references/servlet-opaque-token-resource-server.md) only when the servlet resource server must validate bearer tokens via introspection instead of local JWT verification.

## Dependency baseline

### Core starter and test support

The standalone examples in this section pin the current stable Spring Security BOM, 7.1.0.

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
        <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-bom</artifactId><version>7.1.0</version><type>pom</type><scope>import</scope></dependency>
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

### Authorization server add-on

Use the Boot authorization-server starter when the application acts as an OAuth2 or OIDC provider that issues tokens.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
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
Use [references/jwt-claim-mapping.md](references/jwt-claim-mapping.md) for authority extraction configuration in non-webapp contexts.

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
Open [references/delegated-login-and-oauth2-client.md](references/delegated-login-and-oauth2-client.md) when the application delegates browser login to an external IdP or manages outbound OAuth2 client tokens for downstream calls.

## Coding procedure

1. Keep authorization rules explicit and ordered so public and protected paths are obvious.
2. Match session policy to the client model: use stateless sessions for bearer-token APIs and a stateful session policy only when browser login is part of the design.
3. Configure CORS only for real browser-cross-origin clients, and keep allowed origins, methods, and headers explicit.
4. Keep CSRF policy explicit instead of disabling it reflexively.
   - For stateless APIs, ignore only the paths that do not rely on browser-session cookies.
5. Use a strong `PasswordEncoder` and never store raw passwords.
6. Keep JWT authority mapping, claim interpretation, and principal extraction explicit.
7. Use method security only where request-level rules are not enough.
   - Add `@PostAuthorize`, `@PreFilter`, or `@PostFilter` only when access depends on returned objects or filtered collections.
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

    @MockBean
    JwtDecoder jwtDecoder;

    @Test
    void requiresScope() throws Exception {
        mvc.perform(get("/admin")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
            .andExpect(status().isOk());
    }
}
```

### @WithMockUser test support

```java
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTests {
    @Autowired
    MockMvc mvc;

    @Test
    @WithMockUser(username = "alice@example.com", authorities = "ROLE_ADMIN")
    void adminAccessForAuthenticatedUser() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(status().isOk());
    }

    @Test
    void rejectsAnonymousRequest() throws Exception {
        mvc.perform(get("/admin"))
            .andExpect(status().is3xxRedirection());
    }
}
```

Use `@WithUserDetails` only when the test slice also provides the matching `UserDetailsService` entry for that username.

### Denied-access test shape for API security

```java
@Test
void rejectsAnonymousRequest() throws Exception {
    mvc.perform(get("/admin"))
        .andExpect(status().isUnauthorized());
}
```

Use this 401 expectation only for API/basic/bearer-token style security chains that do not redirect to a login page.

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
4. The JWT validation and claim-mapping strategy, or the opaque-token introspection and principal-mapping strategy, when bearer tokens are involved
5. The test shape proving allowed and denied access
6. Any blocker that requires a reactive, delegated-login, LDAP, SAML2, multi-chain, advanced session, advanced headers, advanced JWT, opaque-token, or custom exception-handling branch

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
- Do not expose broad `/actuator` paths unless explicitly intended.
  - Permitting `/actuator/health` alone does not imply actuator endpoints are publicly accessible; secure any additional actuator endpoints explicitly.

## OAuth 2.1 / OpenID Connect Authorization Server

This section covers building an OAuth 2.1 or OIDC provider with Spring Authorization Server.
Spring Authorization Server is a separate project on its own version line and is the "issuing" side of the same security token model that the resource-server path covers for "enforcement".

Use this section when the application must issue bearer tokens for other applications to consume.
When the application instead validates tokens issued by someone else, use the resource-server path above.

### Authorization server surface map

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| Core provider configuration | The server needs issuer, filter chains, signing keys, clients, and token issuance | Endpoint behavior changes beyond defaults in [references/authorization-server-endpoint-customization.md](references/authorization-server-endpoint-customization.md) |
| Authorization code with PKCE | One interactive client flow is enough for the first provider | Client authentication or proof-of-possession variants are the blocker in [references/authorization-server-client-authentication-variants.md](references/authorization-server-client-authentication-variants.md) or [references/authorization-server-proof-of-possession-variants.md](references/authorization-server-proof-of-possession-variants.md) |
| Token generation | Default JWT issuance needs only small claim customization | Token format, claim mapping, or token-generator composition are the blocker in [references/authorization-server-token-generation-and-customization.md](references/authorization-server-token-generation-and-customization.md) |
| OIDC provider endpoints | The provider must act as an identity provider beyond OAuth2 server behavior | Discovery, UserInfo, logout, or ID-token behavior are the blocker in [references/authorization-server-oidc-provider-endpoints.md](references/authorization-server-oidc-provider-endpoints.md) |
| PAR and advanced request entry | Clients must pre-register authorization parameters at the server | Pushed Authorization Request behavior is the blocker in [references/authorization-server-pushed-authorization-requests.md](references/authorization-server-pushed-authorization-requests.md) |
| Device authorization flow | The client cannot drive a browser redirect flow directly | Device authorization and verification are the blocker in [references/authorization-server-device-authorization-grant.md](references/authorization-server-device-authorization-grant.md) |
| Introspection and revocation | Relying parties or resource servers need token liveness checks or revocation | Introspection or revocation endpoint behavior is the blocker in [references/authorization-server-introspection-and-revocation.md](references/authorization-server-introspection-and-revocation.md) |
| Dynamic client registration | External clients must self-register | Registration security and metadata mapping are the blocker in [references/authorization-server-dynamic-client-registration.md](references/authorization-server-dynamic-client-registration.md) |
| Federation and social login | User authentication comes from an external identity provider | Federated login behavior is the blocker in [references/authorization-server-federated-identity-and-social-login.md](references/authorization-server-federated-identity-and-social-login.md) |
| Extension grants | Built-in grant types do not cover the protocol | Custom grant converters and providers are the blocker in [references/authorization-server-extension-grants.md](references/authorization-server-extension-grants.md) |
| Persistence | In-memory repositories are no longer enough | Relational or Redis-backed state is the blocker in [references/authorization-server-jpa-persistence.md](references/authorization-server-jpa-persistence.md) or [references/authorization-server-redis-persistence.md](references/authorization-server-redis-persistence.md) |
| Multitenancy | One host must serve multiple issuers or tenant-scoped clients | Issuer-scoped component delegation is the blocker in [references/authorization-server-multitenancy.md](references/authorization-server-multitenancy.md) |
| Deployment and testing | The server is moving to production or needs deeper flow verification | Operational hardening is the blocker in [references/authorization-server-deployment-and-operations.md](references/authorization-server-deployment-and-operations.md), deeper authorization-endpoint tests are the blocker in [references/authorization-server-testing-authorization-endpoint.md](references/authorization-server-testing-authorization-endpoint.md), token, metadata, and JWK endpoint tests are the blocker in [references/authorization-server-testing-token-metadata-and-jwk-endpoints.md](references/authorization-server-testing-token-metadata-and-jwk-endpoints.md), OIDC endpoint tests are the blocker in [references/authorization-server-testing-oidc-endpoints.md](references/authorization-server-testing-oidc-endpoints.md), refresh-path tests are the blocker in [references/authorization-server-testing-refresh-path.md](references/authorization-server-testing-refresh-path.md), and introspection, revocation, or consent tests are the blocker in [references/authorization-server-testing-introspection-revocation-and-consent.md](references/authorization-server-testing-introspection-revocation-and-consent.md) |

### Authorization server common path

1. Add the Boot authorization-server starter and fix the issuer URL first.
2. Define one authorization-server `SecurityFilterChain` and one ordinary login `SecurityFilterChain` for user authentication.
3. Start with one `UserDetailsService`, one `RegisteredClientRepository`, one `JWKSource`, one `JwtDecoder`, and one `AuthorizationServerSettings` bean.
4. Register one client explicitly with redirect URIs, scopes, grant types, and PKCE requirements.
5. Use authorization code with PKCE as the default interactive flow, especially for public clients.
6. Keep token customization centralized in one small hook.
7. Enable OIDC only when the provider must serve identity endpoints such as discovery, UserInfo, logout, or ID tokens.
8. Test the authorization, token, metadata, and JWK endpoints before adding persistence or endpoint variants.

### Required authorization server components

- `SecurityFilterChain` for authorization-server endpoints
- `SecurityFilterChain` for the ordinary login path and user authentication support
- `UserDetailsService` for the user who approves or denies authorization
- `RegisteredClientRepository` for OAuth client registration
- `JWKSource<SecurityContext>` for signing keys
- `JwtDecoder` for JWT-backed endpoint processing
- `AuthorizationServerSettings` for issuer and endpoint settings

### Core model roles

| Type | Role |
| --- | --- |
| `RegisteredClient` | client registration: redirect URIs, scopes, grant types, auth method, token settings |
| `OAuth2Authorization` | active authorization state and issued-token linkage |
| `OAuth2AuthorizationConsent` | user-approved scopes for a client |
| authenticated principal | the end user who signs in and authorizes the request |

### Issuer and authorization-server filter chains

```java
@Bean
@Order(1)
SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
    http.oauth2AuthorizationServer(authorizationServer -> http.securityMatcher(authorizationServer.getEndpointsMatcher()));
    return http.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), new MediaTypeRequestMatcher(MediaType.TEXT_HTML))).build();
}

@Bean
@Order(2)
SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .formLogin(Customizer.withDefaults())
        .build();
}

@Bean
AuthorizationServerSettings authorizationServerSettings() {
    return AuthorizationServerSettings.builder().issuer("http://auth-server:9000").build();
}
```

Keep the issuer explicit and stable.
Add `.oidc(Customizer.withDefaults())` only when the provider must expose OIDC endpoints.
Keep the login-side chain separate from issuer-side endpoint configuration.

### User authentication and JWT baseline

```java
@Bean
UserDetailsService users() {
    UserDetails user = User.withUsername("user")
        .password("{noop}password")
        .roles("USER")
        .build();
    return new InMemoryUserDetailsManager(user);
}

@Bean
JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
}
```

### One registered-client baseline

```java
@Bean
RegisteredClientRepository registeredClientRepository() {
    RegisteredClient publicClient = RegisteredClient.withId(UUID.randomUUID().toString())
        .clientId("messaging-client")
        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("http://127.0.0.1:8080/login/oauth2/code/messaging-client")
        .scope("message.read")
        .clientSettings(ClientSettings.builder().requireProofKey(true).build())
        .build();
    return new InMemoryRegisteredClientRepository(publicClient);
}
```

Start with one registered client and one interactive flow.
Default to a public client with PKCE unless the client can truly protect a secret on the server side.

### JWK source baseline

```java
@Bean
JWKSource<SecurityContext> jwkSource() {
    RSAKey rsaKey = generateRsa();
    JWKSet jwkSet = new JWKSet(rsaKey);
    return (selector, securityContext) -> selector.select(jwkSet);
}
```

Keep signing keys centralized and intentional.
Move key loading, rotation, and external key-management decisions into deployment-specific work only after the baseline server is correct.

### Minimal token customizer hook

```java
@Bean
OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
    return context -> {
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            context.getClaims().claim("tenant", "default");
        }
    };
}
```

Keep the customizer small and deterministic.
Add claims only when a downstream resource server or client actually depends on them.

### Protocol endpoint surface

Treat the endpoint surface as part of the provider contract.

#### Baseline endpoints

```text
authorization: /oauth2/authorize
token: /oauth2/token
metadata: /.well-known/oauth-authorization-server
jwk set: /oauth2/jwks
```

#### Conditional endpoints

- Add introspection and revocation when relying parties or resource servers require them.
- Add OIDC provider metadata, UserInfo, logout, and ID tokens only when OIDC is enabled.
- Add PAR, device authorization, or dynamic client registration only when the concrete client protocol requires them.

### Authorization server coding procedure

1. Keep the issuer URL, endpoint exposure, redirect URIs, scopes, and grant types explicit and stable.
2. Start with in-memory repositories for one-node local development, then move to persistent stores only when restart continuity, clustering, or audit requirements exist.
3. Use authorization code with PKCE as the default interactive flow.
   - Treat public-client PKCE as the baseline, not an optional hardening step.
4. Add confidential clients only when the client can actually protect a secret.
5. Keep JWK sourcing, token customization, and endpoint customization centralized instead of scattering logic across unrelated configuration classes.
6. Enable OIDC only when the provider must act as an identity provider, not merely an OAuth2 authorization server.
7. Add PAR, device authorization, introspection, revocation, extension grants, federation, multitenancy, or custom endpoint behavior only after the baseline authorization-code flow works end to end.
8. Test one successful flow and one rejected request path before changing storage or protocol surface area.

### Authorization server minimal validation

- Verify the issuer, authorization endpoint, token endpoint, metadata endpoint, and JWK set endpoint are exposed at the expected paths.
- Verify the registered client accepts the intended redirect URI and rejects an invalid one.
- Verify the authorization-code flow requires PKCE for the public-client baseline.
- Verify token issuance includes only the intended scopes and any minimal custom claims you added.
- Verify one representative invalid request path, such as an unknown client id, invalid scope, or PKCE failure.

### Authorization server production guardrails

- Keep the issuer URL stable and correct behind proxies, TLS termination, and external hostnames.
- Protect signing keys, plan key rotation, and avoid ad hoc per-node key generation in multi-instance deployments.
- Persist registered clients, authorizations, and consent when restart continuity or clustered deployment matters.
- Keep redirect URIs, scopes, and grant types under explicit provisioning control.
- Treat endpoint behavior, token claims, consent behavior, and discovery metadata as compatibility surface for clients.

## References

### Resource server and application security

- Open [references/reactive-webflux-security.md](references/reactive-webflux-security.md) when the task is specifically about `SecurityWebFilterChain` and reactive security behavior.
- Open [references/delegated-login-and-oauth2-client.md](references/delegated-login-and-oauth2-client.md) when the task needs delegated OAuth2 login or outbound OAuth2 client support.
- Open [references/ldap-authentication.md](references/ldap-authentication.md) when the task specifically depends on LDAP-backed authentication.
- Open [references/saml2-login.md](references/saml2-login.md) when the task specifically depends on SAML2.
- Open [references/multiple-security-filter-chains.md](references/multiple-security-filter-chains.md) when path-specific chains or custom filters are required.
- Open [references/jwt-claim-mapping.md](references/jwt-claim-mapping.md) when default JWT conversion is not sufficient or nested/multi-location authority extraction is needed.
- Open [references/security-exception-handling.md](references/security-exception-handling.md) when custom 401 or 403 responses are required.
- Open [references/session-management-and-logout.md](references/session-management-and-logout.md) when concurrent-session control or advanced logout behavior is required.
- Open [references/security-headers.md](references/security-headers.md) when the application needs custom security-header behavior.
- Open [references/servlet-opaque-token-resource-server.md](references/servlet-opaque-token-resource-server.md) only when opaque-token introspection is the required validation path and JWT resource server does not apply.

### Authorization server

- Open [references/authorization-server-client-authentication-variants.md](references/authorization-server-client-authentication-variants.md) when the client is confidential or cannot follow the public-client PKCE baseline.
- Open [references/authorization-server-proof-of-possession-variants.md](references/authorization-server-proof-of-possession-variants.md) when DPoP or MTLS-bound tokens are required.
- Open [references/authorization-server-token-generation-and-customization.md](references/authorization-server-token-generation-and-customization.md) when token formats, claim mapping, token generators, or customizers must change beyond the minimal hook.
- Open [references/authorization-server-oidc-provider-endpoints.md](references/authorization-server-oidc-provider-endpoints.md) when the provider must expose OIDC discovery, UserInfo, logout, or ID tokens.
- Open [references/authorization-server-pushed-authorization-requests.md](references/authorization-server-pushed-authorization-requests.md) when clients require PAR before redirecting the user to authorization.
- Open [references/authorization-server-device-authorization-grant.md](references/authorization-server-device-authorization-grant.md) when the client requires the device authorization and verification flow.
- Open [references/authorization-server-introspection-and-revocation.md](references/authorization-server-introspection-and-revocation.md) when the blocker is token liveness inspection or token revocation.
- Open [references/authorization-server-dynamic-client-registration.md](references/authorization-server-dynamic-client-registration.md) when external clients need self-service registration.
- Open [references/authorization-server-federated-identity-and-social-login.md](references/authorization-server-federated-identity-and-social-login.md) when the authorization server must authenticate users through an external identity provider.
- Open [references/authorization-server-extension-grants.md](references/authorization-server-extension-grants.md) when the ordinary authorization-code flow is not enough and a custom grant type or token exchange path is required.
- Open [references/authorization-server-jpa-persistence.md](references/authorization-server-jpa-persistence.md) when registered clients, authorizations, or consent must live in a relational database.
- Open [references/authorization-server-redis-persistence.md](references/authorization-server-redis-persistence.md) when authorization state, consent, or operational token data must live in Redis.
- Open [references/authorization-server-multitenancy.md](references/authorization-server-multitenancy.md) when issuer resolution, issuer-scoped clients, or issuer-scoped signing keys differ by tenant.
- Open [references/authorization-server-endpoint-customization.md](references/authorization-server-endpoint-customization.md) when authorization, token, metadata, or consent endpoints need custom request or response handling.
- Open [references/authorization-server-deployment-and-operations.md](references/authorization-server-deployment-and-operations.md) when the server is moving behind proxies, into production, or into operational hardening work.
- Open [references/authorization-server-upgrade-spring-authorization-server.md](references/authorization-server-upgrade-spring-authorization-server.md) when moving between Spring Authorization Server versions or replacing Spring Security OAuth server behavior.
- Open [references/authorization-server-migrate-spring-security-6-to-7.md](references/authorization-server-migrate-spring-security-6-to-7.md) when the blocker is Spring Security 6 to 7 migration work around the authorization server.
- Open [references/authorization-server-testing-authorization-endpoint.md](references/authorization-server-testing-authorization-endpoint.md) when the task needs deeper authorization-endpoint tests.
- Open [references/authorization-server-testing-token-metadata-and-jwk-endpoints.md](references/authorization-server-testing-token-metadata-and-jwk-endpoints.md) when the task needs token, metadata, or JWK endpoint tests.
- Open [references/authorization-server-testing-oidc-endpoints.md](references/authorization-server-testing-oidc-endpoints.md) when the task needs OIDC endpoint verification.
- Open [references/authorization-server-testing-refresh-path.md](references/authorization-server-testing-refresh-path.md) when the task needs refresh-token path verification.
- Open [references/authorization-server-testing-introspection-revocation-and-consent.md](references/authorization-server-testing-introspection-revocation-and-consent.md) when the task needs introspection, revocation, or consent verification.
