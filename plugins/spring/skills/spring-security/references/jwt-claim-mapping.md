# Spring Security JWT claim mapping

Open this reference when the default resource-server JWT conversion is not enough because authorities, principal name, issuer rules, audience rules, or accepted algorithms must be customized.

Keep issuer validation, audience validation, authority mapping, and principal-claim selection explicit.

## Custom authority mapping

Use `JwtGrantedAuthoritiesConverter` when the token does not use Spring Security's default `scope` or `scp` conventions.

```java
@Bean
Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName("roles");
    authorities.setAuthorityPrefix("ROLE_");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    converter.setPrincipalClaimName("preferred_username");
    return converter;
}
```

Wire the converter into the resource-server DSL.

```java
.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
```

## Decoder validation

Use an explicit decoder when issuer, audience, or algorithm requirements are part of the contract.

```java
@Bean
JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation("https://issuer.example.com");

    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer("https://issuer.example.com");
    OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<List<String>>(
        "aud",
        audience -> audience != null && audience.contains("invoices-api")
    );

    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
    return decoder;
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Token authorities live in `roles` | `JwtGrantedAuthoritiesConverter#setAuthoritiesClaimName("roles")` |
| Authorities should become Spring roles | set `ROLE_` prefix and use `hasRole(...)` |
| Principal name should come from `preferred_username` or `email` | `JwtAuthenticationConverter#setPrincipalClaimName(...)` |
| API requires an audience check | add a `JwtClaimValidator` for `aud` |
| Issuer is a third-party that uses non-standard claim names | custom `JwtGrantedAuthoritiesConverter` and principal claim |
| Algorithm or signing key must be restricted | explicit `NimbusJwtDecoder` with `SignatureAlgorithm.RS256` and public key |

## Version-grounded claims (Spring Security 7)

Spring Security 7 applies timestamp validation by default. Issuer validation becomes part of the default validator set when the decoder is built from `issuer-uri` or `JwtDecoders.fromIssuerLocation(...)`.

- `iss` (issuer) validation is enforced when the decoder comes from issuer metadata.
- `aud` (audience) validation is off by default; add a `JwtClaimValidator` when the API audience is fixed.
- `exp` (expiration) validation is on by default; do not disable it for production use.
- `nbf` (not before) validation is on by default when present in the token.

When customizing for a specific provider such as Auth0, Okta, or Keycloak, verify the provider's claim names match Spring Security's defaults. Providers often use `scope` (space-separated string) rather than `scp` (array).

## Gotchas

- Do not mix `ROLE_` conventions and `SCOPE_` conventions accidentally.
- Do not assume a third-party issuer uses `scope` or `scp`.
- Do not leave audience validation implicit when downstream authorization depends on token audience.
- Do not disable `exp` or `iss` validation in production unless the deployment model genuinely requires it.
- Do not assume the principal name is `sub` for third-party issuers; many use `preferred_username` or `email`.
