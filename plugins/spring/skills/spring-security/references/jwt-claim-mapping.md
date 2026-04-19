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
.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
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

## Gotchas

- Do not mix `ROLE_` conventions and `SCOPE_` conventions accidentally.
- Do not assume a third-party issuer uses `scope` or `scp`.
- Do not leave audience validation implicit when downstream authorization depends on token audience.
