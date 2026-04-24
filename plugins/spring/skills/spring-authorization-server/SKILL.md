---
name: "spring-authorization-server"
description: "Use this skill when implementing an OAuth2 or OpenID Connect provider with Spring Authorization Server, including registered clients, authorization code with PKCE, token issuance, JWK exposure, consent, PAR, device authorization, introspection, revocation, and provider configuration."
metadata:
  title: "Spring Authorization Server"
  official_project_url: "https://spring.io/projects/spring-authorization-server"
  reference_doc_urls:
    - "https://docs.spring.io/spring-authorization-server/reference/index.html"
    - "https://docs.spring.io/spring-authorization-server/reference/configuration-model.html"
    - "https://docs.spring.io/spring-authorization-server/reference/core-model-components.html"
    - "https://docs.spring.io/spring-authorization-server/reference/protocol-endpoints.html"
  version: "1.5.7"
---

Use this skill when implementing an OAuth2 or OpenID Connect provider with Spring Authorization Server, including registered clients, authorization code with PKCE, token issuance, JWK exposure, consent, PAR, device authorization, introspection, revocation, and provider configuration.

## Boundaries

Use `spring-authorization-server` for provider-side OAuth2 or OIDC endpoints, token issuance, registered client configuration, JWK publication, authorization consent, and issuer configuration.

- Use `spring-security` for application login, resource-server token validation, or ordinary web security that does not issue OAuth2 tokens.
- Keep provider concerns separate from client application concerns. This skill is for the issuer, not for the consuming OAuth2 client.
- Keep the ordinary path focused on one issuer, one registered-client baseline, one signing-key source, and authorization code with PKCE before adding persistence, federation, device flows, PAR, custom grants, or multitenancy.

## Official surface map

Use this map to keep the current stable Spring Authorization Server 1.5.x surface visible without pushing every protocol branch into `references/`. Spring Authorization Server is a standalone project that builds on Spring Security; version advice here targets the SAS artifact line (`org.springframework.security:spring-security-oauth2-authorization-server`), not Spring Security's release cadence.

| Surface | Start here when | Open a reference when |
| --- | --- | --- |
| Core provider configuration | The server needs issuer, filter chains, signing keys, clients, and token issuance | Endpoint behavior changes beyond defaults in [references/endpoint-customization.md](references/endpoint-customization.md) |
| Authorization code with PKCE | One interactive client flow is enough for the first provider | Client authentication or proof-of-possession variants are the blocker in [references/client-authentication-variants.md](references/client-authentication-variants.md) or [references/proof-of-possession-variants.md](references/proof-of-possession-variants.md) |
| Token generation | Default JWT issuance needs only small claim customization | Token format, claim mapping, or token-generator composition are the blocker in [references/token-generation-and-customization.md](references/token-generation-and-customization.md) |
| OIDC provider endpoints | The provider must act as an identity provider, not only an OAuth2 server | Discovery, UserInfo, logout, or ID-token behavior are the blocker in [references/oidc-provider-endpoints.md](references/oidc-provider-endpoints.md) |
| PAR and advanced request entry | Clients must pre-register authorization parameters at the server | Pushed Authorization Request behavior is the blocker in [references/pushed-authorization-requests.md](references/pushed-authorization-requests.md) |
| Device authorization flow | The client cannot drive a browser redirect flow directly | Device authorization and verification are the blocker in [references/device-authorization-grant.md](references/device-authorization-grant.md) |
| Introspection and revocation | Relying parties or resource servers need token liveness checks or revocation | Introspection or revocation endpoint behavior is the blocker in [references/introspection-and-revocation.md](references/introspection-and-revocation.md) |
| Dynamic client registration | External clients must self-register | Registration security and metadata mapping are the blocker in [references/dynamic-client-registration.md](references/dynamic-client-registration.md) |
| Federation and social login | User authentication comes from an external identity provider | Federated login behavior is the blocker in [references/federated-identity-and-social-login.md](references/federated-identity-and-social-login.md) |
| Extension grants | Built-in grant types do not cover the protocol | Custom grant converters and providers are the blocker in [references/extension-grants.md](references/extension-grants.md) |
| Persistence | In-memory repositories are no longer enough | Relational or Redis-backed state is the blocker in [references/jpa-persistence.md](references/jpa-persistence.md) or [references/redis-persistence.md](references/redis-persistence.md) |
| Multitenancy | One host must serve multiple issuers or tenant-scoped clients | Issuer-scoped component delegation is the blocker in [references/multitenancy.md](references/multitenancy.md) |
| Deployment and testing | The server is moving to production or needs deeper flow verification | Operational hardening is the blocker in [references/deployment-and-operations.md](references/deployment-and-operations.md), deeper authorization-endpoint tests are the blocker in [references/testing-authorization-endpoint.md](references/testing-authorization-endpoint.md), token, metadata, and JWK endpoint tests are the blocker in [references/testing-token-metadata-and-jwk-endpoints.md](references/testing-token-metadata-and-jwk-endpoints.md), OIDC endpoint tests are the blocker in [references/testing-oidc-endpoints.md](references/testing-oidc-endpoints.md), refresh-path tests are the blocker in [references/testing-refresh-path.md](references/testing-refresh-path.md), and introspection, revocation, or consent tests are the blocker in [references/testing-introspection-revocation-and-consent.md](references/testing-introspection-revocation-and-consent.md) |

## Common path

The ordinary Spring Authorization Server job is:

1. Add the Boot authorization-server starter and fix the issuer URL first.
2. Define one authorization-server `SecurityFilterChain` and one ordinary login `SecurityFilterChain` for user authentication.
3. Start with one `UserDetailsService`, one `RegisteredClientRepository`, one `JWKSource`, one `JwtDecoder`, and one `AuthorizationServerSettings` bean.
4. Register one client explicitly with redirect URIs, scopes, grant types, and PKCE requirements.
5. Use authorization code with PKCE as the default interactive flow, especially for public clients.
6. Keep token customization centralized in one small hook.
7. Enable OIDC only when the provider must serve identity endpoints such as discovery, UserInfo, logout, or ID tokens.
8. Test the authorization, token, metadata, and JWK endpoints before adding persistence or endpoint variants.

## Dependency baseline

Use the Boot starter for the ordinary authorization-server path and add test support for endpoint verification.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## Required components

The minimum provider needs these components wired intentionally:

- `SecurityFilterChain` for authorization-server endpoints
- ordinary login path and user authentication support
- `UserDetailsService` for the user who approves or denies authorization
- `RegisteredClientRepository` for OAuth client registration
- `JWKSource<SecurityContext>` for signing keys
- `JwtDecoder` for JWT-backed endpoint processing
- `AuthorizationServerSettings` for issuer and endpoint settings

## Core model roles

Keep the core model vocabulary explicit:

| Type | Role |
| --- | --- |
| `RegisteredClient` | client registration: redirect URIs, scopes, grant types, auth method, token settings |
| `OAuth2Authorization` | active authorization state and issued-token linkage |
| `OAuth2AuthorizationConsent` | user-approved scopes for a client |
| authenticated principal | the end user who signs in and authorizes the request |

## First safe configuration

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

Keep the issuer explicit and stable. Add `.oidc(Customizer.withDefaults())` only when the provider must expose OIDC endpoints. Keep the login-side chain separate from issuer-side endpoint configuration.

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

Start with one registered client and one interactive flow. Default to a public client with PKCE unless the client can truly protect a secret on the server side.

### JWK source baseline

```java
@Bean
JWKSource<SecurityContext> jwkSource() {
    RSAKey rsaKey = generateRsa();
    JWKSet jwkSet = new JWKSet(rsaKey);
    return (selector, securityContext) -> selector.select(jwkSet);
}
```

Keep signing keys centralized and intentional. Move key loading, rotation, and external key-management decisions into deployment-specific work only after the baseline server is correct.

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

Keep the customizer small and deterministic. Add claims only when a downstream resource server or client actually depends on them.

## Protocol endpoint surface

Treat the endpoint surface as part of the provider contract.

### Baseline endpoints

```text
authorization: /oauth2/authorize
token: /oauth2/token
metadata: /.well-known/oauth-authorization-server
jwk set: /oauth2/jwks
```

### Conditional endpoints

- Add introspection and revocation when relying parties or resource servers require them.
- Add OIDC provider metadata, UserInfo, logout, and ID tokens only when OIDC is enabled.
- Add PAR, device authorization, or dynamic client registration only when the concrete client protocol requires them.

## Coding procedure

1. Keep the issuer URL, endpoint exposure, redirect URIs, scopes, and grant types explicit and stable.
2. Start with in-memory repositories for one-node local development, then move to persistent stores only when restart continuity, clustering, or audit requirements exist.
3. Use authorization code with PKCE as the default interactive flow. Treat public-client PKCE as the baseline, not an optional hardening step.
4. Add confidential clients only when the client can actually protect a secret.
5. Keep JWK sourcing, token customization, and endpoint customization centralized instead of scattering logic across unrelated configuration classes.
6. Enable OIDC only when the provider must act as an identity provider, not merely an OAuth2 authorization server.
7. Add PAR, device authorization, introspection, revocation, extension grants, federation, multitenancy, or custom endpoint behavior only after the baseline authorization-code flow works end to end.
8. Test one successful flow and one rejected request path before changing storage or protocol surface area.

## Minimal validation

- Verify the issuer, authorization endpoint, token endpoint, metadata endpoint, and JWK set endpoint are exposed at the expected paths.
- Verify the registered client accepts the intended redirect URI and rejects an invalid one.
- Verify the authorization-code flow requires PKCE for the public-client baseline.
- Verify token issuance includes only the intended scopes and any minimal custom claims you added.
- Verify one representative invalid request path, such as an unknown client id, invalid scope, or PKCE failure.

## Production guardrails

- Keep the issuer URL stable and correct behind proxies, TLS termination, and external hostnames.
- Protect signing keys, plan key rotation, and avoid ad hoc per-node key generation in multi-instance deployments.
- Persist registered clients, authorizations, and consent when restart continuity or clustered deployment matters.
- Keep redirect URIs, scopes, and grant types under explicit provisioning control.
- Treat endpoint behavior, token claims, consent behavior, and discovery metadata as compatibility surface for clients.

## References

- Open [references/client-authentication-variants.md](references/client-authentication-variants.md) when the client is confidential or cannot follow the public-client PKCE baseline.
- Open [references/proof-of-possession-variants.md](references/proof-of-possession-variants.md) when DPoP or MTLS-bound tokens are required.
- Open [references/token-generation-and-customization.md](references/token-generation-and-customization.md) when token formats, claim mapping, token generators, or customizers must change beyond the minimal hook.
- Open [references/oidc-provider-endpoints.md](references/oidc-provider-endpoints.md) when the provider must expose OIDC discovery, UserInfo, logout, or ID tokens.
- Open [references/pushed-authorization-requests.md](references/pushed-authorization-requests.md) when clients require PAR before redirecting the user to authorization.
- Open [references/device-authorization-grant.md](references/device-authorization-grant.md) when the client requires the device authorization and verification flow.
- Open [references/introspection-and-revocation.md](references/introspection-and-revocation.md) when the blocker is token liveness inspection or token revocation.
- Open [references/dynamic-client-registration.md](references/dynamic-client-registration.md) when external clients need self-service registration.
- Open [references/federated-identity-and-social-login.md](references/federated-identity-and-social-login.md) when the authorization server must authenticate users through an external identity provider.
- Open [references/extension-grants.md](references/extension-grants.md) when the ordinary authorization-code flow is not enough and a custom grant type or token exchange path is required.
- Open [references/jpa-persistence.md](references/jpa-persistence.md) when registered clients, authorizations, or consent must live in a relational database.
- Open [references/redis-persistence.md](references/redis-persistence.md) when authorization state, consent, or operational token data must live in Redis.
- Open [references/multitenancy.md](references/multitenancy.md) when issuer resolution, issuer-scoped clients, or issuer-scoped signing keys differ by tenant.
- Open [references/endpoint-customization.md](references/endpoint-customization.md) when authorization, token, metadata, or consent endpoints need custom request or response handling.
- Open [references/deployment-and-operations.md](references/deployment-and-operations.md) when the server is moving behind proxies, into production, or into operational hardening work.
- Open [references/upgrade-spring-authorization-server.md](references/upgrade-spring-authorization-server.md) when moving between Spring Authorization Server versions or replacing legacy Spring Security OAuth server behavior.
- Open [references/migrate-spring-security-6-to-7.md](references/migrate-spring-security-6-to-7.md) when the blocker is Spring Security 6 to 7 migration work around the authorization server.
- Open [references/testing-authorization-endpoint.md](references/testing-authorization-endpoint.md) when the task needs deeper authorization-endpoint tests.
- Open [references/testing-token-metadata-and-jwk-endpoints.md](references/testing-token-metadata-and-jwk-endpoints.md) when the task needs token, metadata, or JWK endpoint tests.
- Open [references/testing-oidc-endpoints.md](references/testing-oidc-endpoints.md) when the task needs OIDC endpoint verification.
- Open [references/testing-refresh-path.md](references/testing-refresh-path.md) when the task needs refresh-token path verification.
- Open [references/testing-introspection-revocation-and-consent.md](references/testing-introspection-revocation-and-consent.md) when the task needs introspection, revocation, or consent verification.
