# Token generation and customization

Open this reference when the minimal JWT customizer in [SKILL.md](../SKILL.md) is not enough and the blocker is token format, token-generator composition, or JWT or opaque-token claim customization.

## When to open

- The issued access token must contain custom claims.
- A token generator must be replaced or the default JWT encoding must be customized.
- The choice between JWT and opaque token format is unclear.
- Access tokens and ID tokens need separate customization paths.
- A custom claim set or format must be produced for a specific client or scope.

## JWT vs opaque decision

**JWT format** embeds claims in the token itself. Use JWT when:

- The resource server can validate the signature directly.
- Claims must be inspected without a database round-trip.
- Token integrity must be verifiable offline.

**Opaque format** requires a token introspection call. Use opaque when:

- Tokens must be revocable without signature change.
- Token size must be minimal.
- The authorization server must retain full control after issuance.

```java
TokenSettings.builder()
    .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
    .build();

TokenSettings.builder()
    .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
    .build();
```

## Token generator customization

Replace the default `OAuth2TokenGenerator` only when the default generator chain is not enough.

```java
@Bean
OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator(JWKSource<SecurityContext> jwkSource) {
    JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
    return new DelegatingOAuth2TokenGenerator(jwtGenerator);
}
```

The default generator chain handles access tokens, refresh tokens, and ID tokens based on `TokenSettings`.

## JWT customizer hook

Use one `OAuth2TokenCustomizer<JwtEncodingContext>` bean to modify JWT headers or claims before signing:

```java
@Bean
OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
    return context -> {
        JwtClaimsSet.Builder claims = context.getClaims();
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            claims.claim("tenant", context.getRegisteredClient().getClientId());
        }
        if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            claims.claim("tenant", context.getRegisteredClient().getClientId());
        }
    };
}
```

Define this bean once. Spring Authorization Server wires it into the JWT generation path automatically.

## Opaque-token claim customizer

Use `OAuth2TokenCustomizer<OAuth2TokenClaimsContext>` when the access-token format is opaque and the claim set must still be customized.

```java
@Bean
OAuth2TokenCustomizer<OAuth2TokenClaimsContext> accessTokenCustomizer() {
    return context -> context.getClaims().claim("tenant", context.getRegisteredClient().getClientId());
}
```

## ID token customization

ID tokens are generated only when OIDC is enabled. Customize them through the same `OAuth2TokenCustomizer<JwtEncodingContext>` by checking `OidcParameterNames.ID_TOKEN`.

## Token lifetime settings

Adjust token lifetimes in `TokenSettings`:

```java
TokenSettings.builder()
    .reuseRefreshTokens(false)
    .refreshTokenTimeToLive(Duration.ofHours(24))
    .accessTokenTimeToLive(Duration.ofMinutes(60))
    .build();
```

This setting issues a new refresh token on each exchange instead of reusing the existing one.

## Claims from authorization state

Read authorization attributes or principal data inside the token-customizer context when claims must depend on the authorization state:

```java
@Bean
OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
    return context -> {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        OAuth2Authorization authorization = context.getAuthorization();
        if (authorization != null) {
            context.getClaims().claim("amr", authorization.getAttributes().get("amr"));
        }
    };
}
```

## Per-client token settings

Override token settings for specific clients:

```java
RegisteredClient restrictedClient = RegisteredClient.withId(UUID.randomUUID().toString())
    .clientId("short-lived-client")
    .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofMinutes(5)).build())
    .build();
```

## Official references

- [Core model components](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/core-model-components.html)
- [Configuration model](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/configuration-model.html)
- [Protocol endpoints](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/protocol-endpoints.html)
