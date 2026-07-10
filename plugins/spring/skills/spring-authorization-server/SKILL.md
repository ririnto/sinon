---
name: spring-authorization-server
description: >-
  Build OAuth 2.1 and OpenID Connect providers with Spring Security authorization-server support, registered clients, PKCE, token issuance, signing keys, consent, and protocol endpoints.
  Use when an application must issue access or ID tokens, publish authorization-server metadata or JWKs, manage OAuth clients, or implement PAR, device authorization, introspection, revocation, and extension grants.
---

# Spring Authorization Server

## Boundaries

Use this skill when the application is the token issuer: it authenticates users or clients, records consent, issues tokens, publishes provider metadata, and owns signing keys.
Bearer-token validation in an API, delegated login against an external provider, and outbound OAuth2 client token management are relying-party concerns rather than token-issuance work.

Spring Authorization Server 1.5.8 is the final standalone generation.
Active authorization-server development continues in Spring Security 7, where `spring-security-oauth2-authorization-server` shares Spring Security's version and BOM line.
The examples below use the Spring Boot 4.1 managed path.

## Common path

1. Add the Boot authorization-server starter and fix the issuer URL first.
2. Define one authorization-server `SecurityFilterChain` and one login `SecurityFilterChain` for user authentication.
3. Register one user, one public client, one signing key source, one `JwtDecoder`, and one `AuthorizationServerSettings` bean.
4. Use authorization code with PKCE as the first interactive flow.
5. Verify the issuer, metadata, authorization, token, and JWK endpoints before changing persistence or protocol surface.
6. Add OIDC, confidential clients, PAR, device authorization, introspection, revocation, federation, or extension grants only when the client contract requires them.

## Dependency baseline

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

Use Spring Boot dependency management for this path.
Do not add a standalone Spring Authorization Server 1.5.x version beside Spring Security 7 modules.

## First safe commands

```sh
./mvnw test
```

```sh
./gradlew test
```

After starting the local server, inspect the published contract:

```sh
curl -fsS http://localhost:9000/.well-known/oauth-authorization-server
curl -fsS http://localhost:9000/oauth2/jwks
```

## Required components

- An ordered authorization-server filter chain for protocol endpoints
- A login filter chain that authenticates the resource owner
- A `RegisteredClientRepository`
- A `JWKSource<SecurityContext>` and `JwtDecoder`
- An explicit `AuthorizationServerSettings` issuer
- Durable client, authorization, and consent stores before clustered production deployment

## Filter chains and issuer

```java
@Bean
@Order(1)
SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
    http.oauth2AuthorizationServer(authorizationServer -> http.securityMatcher(authorizationServer.getEndpointsMatcher()));
    return http
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
        .build();
}

@Bean
@Order(2)
SecurityFilterChain loginSecurityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .formLogin(Customizer.withDefaults())
        .build();
}

@Bean
AuthorizationServerSettings authorizationServerSettings() {
    return AuthorizationServerSettings.builder().issuer("http://localhost:9000").build();
}
```

Keep the issuer stable and equal to the externally visible URL behind proxies and TLS termination.
Add `.oidc(Customizer.withDefaults())` to the authorization-server configurer only when the provider must publish OIDC identity endpoints and ID tokens.

## Development user and public client

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

The `{noop}` password and in-memory repositories are local-development shapes only.
Use a public client with PKCE unless the client runs on a server that can protect a secret.

## Signing key and decoder

```java
@Bean
JWKSource<SecurityContext> jwkSource() {
    RSAKey rsaKey = generateRsa();
    JWKSet jwkSet = new JWKSet(rsaKey);
    return (selector, context) -> selector.select(jwkSet);
}

@Bean
JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
}

private static RSAKey generateRsa() {
    KeyPair keyPair = generateRsaKey();
    RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
    RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
    return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(UUID.randomUUID().toString()).build();
}

private static KeyPair generateRsaKey() {
    try {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
    catch (GeneralSecurityException ex) {
        throw new IllegalStateException("Failed to generate RSA key", ex);
    }
}
```

Generate ephemeral keys only for local development.
Load protected, rotation-aware signing material from the deployment's key-management boundary before production.

## Minimal token customization

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

Add only claims that a documented resource-server or client contract consumes.
Keep token customization centralized so the issued-token schema remains reviewable.

## Protocol surface

Baseline endpoints:

```text
authorization: /oauth2/authorize
token: /oauth2/token
metadata: /.well-known/oauth-authorization-server
jwk set: /oauth2/jwks
```

OIDC discovery, UserInfo, logout, device authorization, PAR, dynamic registration, introspection, and revocation are conditional surfaces.
Do not publish them until the concrete client protocol requires them and their access policy is tested.

## Validation checklist

- Verify metadata reports the intended issuer and endpoint URLs.
- Verify the registered redirect URI succeeds and an unregistered URI fails.
- Verify the public client must present a valid PKCE verifier.
- Verify issued tokens contain only approved scopes and claims.
- Verify an unknown client, invalid scope, or invalid PKCE request is rejected.
- Verify signing keys and authorization state survive restart before a clustered production rollout.

## Production guardrails

- Keep the issuer stable behind proxies, external hostnames, and TLS termination.
- Protect signing keys and plan overlapping key rotation.
- Persist registered clients, authorizations, and consent when restart continuity matters.
- Provision redirect URIs, scopes, grant types, and client authentication methods explicitly.
- Treat endpoint behavior, token claims, consent, and discovery metadata as client compatibility surfaces.

## Output contract

Return:

1. The issuer, client, grant, scope, and endpoint contract
2. The filter-chain, client repository, signing-key, decoder, and settings configuration
3. The successful flow and rejected-request validation shape
4. Any persistence, key-rotation, proxy, OIDC, or advanced-protocol blocker

## References

- Open [references/authorization-server-client-authentication-variants.md](references/authorization-server-client-authentication-variants.md) when a confidential client or non-default token-endpoint authentication method is required.
- Open [references/authorization-server-proof-of-possession-variants.md](references/authorization-server-proof-of-possession-variants.md) when DPoP or MTLS-bound tokens are required.
- Open [references/authorization-server-token-generation-and-customization.md](references/authorization-server-token-generation-and-customization.md) when token formats, generators, or claims differ from the minimal JWT hook.
- Open [references/authorization-server-oidc-provider-endpoints.md](references/authorization-server-oidc-provider-endpoints.md) when OIDC discovery, UserInfo, logout, or ID-token behavior is required.
- Open [references/authorization-server-pushed-authorization-requests.md](references/authorization-server-pushed-authorization-requests.md) when clients require PAR.
- Open [references/authorization-server-device-authorization-grant.md](references/authorization-server-device-authorization-grant.md) when browserless clients require device authorization.
- Open [references/authorization-server-introspection-and-revocation.md](references/authorization-server-introspection-and-revocation.md) when token liveness inspection or revocation is required.
- Open [references/authorization-server-dynamic-client-registration.md](references/authorization-server-dynamic-client-registration.md) when external clients need self-service registration.
- Open [references/authorization-server-federated-identity-and-social-login.md](references/authorization-server-federated-identity-and-social-login.md) when user authentication is delegated to an external provider.
- Open [references/authorization-server-extension-grants.md](references/authorization-server-extension-grants.md) when an extension grant or token exchange path is required.
- Open [references/authorization-server-jpa-persistence.md](references/authorization-server-jpa-persistence.md) or [references/authorization-server-redis-persistence.md](references/authorization-server-redis-persistence.md) when authorization state must survive restart.
- Open [references/authorization-server-multitenancy.md](references/authorization-server-multitenancy.md) when issuer, clients, or signing keys differ by tenant.
- Open [references/authorization-server-endpoint-customization.md](references/authorization-server-endpoint-customization.md) when protocol endpoint request or response handling must change.
- Open [references/authorization-server-deployment-and-operations.md](references/authorization-server-deployment-and-operations.md) when proxying, production hardening, or key operations are the blocker.
- Open [references/authorization-server-upgrade-spring-authorization-server.md](references/authorization-server-upgrade-spring-authorization-server.md) or [references/authorization-server-migrate-spring-security-6-to-7.md](references/authorization-server-migrate-spring-security-6-to-7.md) when the blocker is version migration.
- Open [references/authorization-server-testing-authorization-endpoint.md](references/authorization-server-testing-authorization-endpoint.md) or [references/authorization-server-testing-refresh-path.md](references/authorization-server-testing-refresh-path.md) when authorization-code or refresh-token behavior needs deeper verification.
- Open [references/authorization-server-testing-token-metadata-and-jwk-endpoints.md](references/authorization-server-testing-token-metadata-and-jwk-endpoints.md), [references/authorization-server-testing-oidc-endpoints.md](references/authorization-server-testing-oidc-endpoints.md), or [references/authorization-server-testing-introspection-revocation-and-consent.md](references/authorization-server-testing-introspection-revocation-and-consent.md) when the corresponding protocol endpoints need deeper request and response verification.
