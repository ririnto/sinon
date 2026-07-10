# Spring Security JWT claim mapping

Open this reference when the default resource-server JWT conversion is not enough because authorities, principal name, issuer rules, audience rules, or accepted algorithms must be customized, or when authorities live in nested or multi-location claims.

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

## Nested claim extraction with SpEL (Spring Boot 4.1+ / Spring Security 7.1+)

Use `ExpressionJwtGrantedAuthoritiesConverter` when authorities live in nested or complex claim structures that `JwtGrantedAuthoritiesConverter#setAuthoritiesClaimName` cannot reach with a simple name.

### Property-first approach (no custom bean)

Spring Boot 4.1 auto-configures `ExpressionJwtGrantedAuthoritiesConverter` when `authorities-claim-expressions` is set.
This property is mutually exclusive with `authorities-claim-name` and `authorities-claim-delimiter`.

Extract Keycloak client roles from `resource_access.<client-id>.roles`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.example.com/realms/myrealm
          authorities-claim-expressions:
            - '["resource_access"]["my-client-id"]["roles"]'
          authority-prefix: ROLE_
```

Extract from a single nested claim:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.example.com/realms/myrealm
          authorities-claim-expressions:
            - '[nested][scopes]'
```

Combine realm roles and client roles into a single authority set:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://keycloak.example.com/realms/myrealm
          authorities-claim-expressions:
            - '["realm_access"]["roles"]'
            - '["resource_access"]["my-client-id"]["roles"]'
          authority-prefix: ROLE_
```

Supported in both servlet and reactive applications.
Set `authority-prefix` to override the `SCOPE_` default.

### Programmatic approach

When property-based configuration is not sufficient, define the converter as a bean.

Single nested location:

```java
@Bean
Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
    SpelExpressionParser parser = new SpelExpressionParser();
    Expression expression = parser.parseExpression("[nested][scopes]");
    ExpressionJwtGrantedAuthoritiesConverter authorities =
            new ExpressionJwtGrantedAuthoritiesConverter(expression);
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
}
```

### Multi-location extraction

Combine multiple converters with `DelegatingJwtGrantedAuthoritiesConverter`:

```java
@Bean
Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
    SpelExpressionParser parser = new SpelExpressionParser();
    ExpressionJwtGrantedAuthoritiesConverter realmRoles = new ExpressionJwtGrantedAuthoritiesConverter(parser.parseExpression("[\"realm_access\"][\"roles\"]"));
    realmRoles.setAuthorityPrefix("ROLE_");
    ExpressionJwtGrantedAuthoritiesConverter clientRoles = new ExpressionJwtGrantedAuthoritiesConverter(parser.parseExpression("[\"resource_access\"][\"my-client-id\"][\"roles\"]"));
    clientRoles.setAuthorityPrefix("ROLE_");
    DelegatingJwtGrantedAuthoritiesConverter combined = new DelegatingJwtGrantedAuthoritiesConverter(realmRoles, clientRoles);
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(combined);
    return converter;
}
```

The SpEL expression result must be a `Collection`.
Do not use `authorities-claim-expressions` together with `authorities-claim-name` or `authorities-claim-delimiter` -- they are mutually exclusive.

## Decoder validation

Use an explicit decoder when issuer, audience, or algorithm requirements are part of the contract.

```java
@Bean
JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation("https://issuer.example.com");
    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer("https://issuer.example.com");
    OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<List<String>>("aud", audience -> audience != null && audience.contains("invoices-api"));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
    return decoder;
}
```

## Non-webapp resource server (Spring Boot 4.1+)

Spring Boot 4.1 auto-configures `JwtDecoder`, `ReactiveJwtDecoder`, `OpaqueTokenIntrospector`, and the JWT authentication converter in non-web applications.
No `SecurityFilterChain` bean is created.
Authenticate tokens programmatically with the auto-configured decoder.

```java
@Autowired
JwtDecoder jwtDecoder;
void validateToken(String token) {
    Jwt jwt = jwtDecoder.decode(token);
}
```

Authority extraction properties (`authorities-claim-name`, `authorities-claim-expressions`, `authority-prefix`) work in non-webapp contexts.
Spring Boot 4.0.x requires a servlet or reactive web server for resource server auto-configuration.

## Decision points

| Situation | Use |
| --- | --- |
| Token authorities live in a top-level `roles` claim | `JwtGrantedAuthoritiesConverter#setAuthoritiesClaimName("roles")` |
| Authorities should become Spring roles | set `ROLE_` prefix and use `hasRole(...)` |
| Authorities live in nested claims (e.g. Keycloak `resource_access`) | `authorities-claim-expressions` property or `ExpressionJwtGrantedAuthoritiesConverter` |
| Authorities come from multiple claim locations | `authorities-claim-expressions` list or `DelegatingJwtGrantedAuthoritiesConverter` |
| Principal name should come from `preferred_username` or `email` | `JwtAuthenticationConverter#setPrincipalClaimName(...)` |
| API requires an audience check | add a `JwtClaimValidator` for `aud` |
| Issuer is a third-party that uses non-standard claim names | custom `JwtGrantedAuthoritiesConverter` and principal claim |
| Algorithm or signing key must be restricted | explicit `NimbusJwtDecoder` with `SignatureAlgorithm.RS256` and public key |
| Non-webapp needs token validation without a web server | Spring Boot 4.1+ non-webapp resource server auto-configuration |

## Version-grounded claims (Spring Security 7)

Spring Security 7 applies timestamp validation by default.
Issuer validation becomes part of the default validator set when the decoder is built from `issuer-uri` or `JwtDecoders.fromIssuerLocation(...)`.

- `iss` (issuer) validation is enforced when the decoder comes from issuer metadata.
- `aud` (audience) validation is off by default.
  - Add a `JwtClaimValidator` when the API audience is fixed.
- `exp` (expiration) validation is on by default.
  - Do not disable it for production use.
- `nbf` (not before) validation is on by default when present in the token.

When customizing for a specific provider such as Auth0, Okta, or Keycloak, verify the provider's claim names match Spring Security's defaults.
Providers often use `scope` (space-separated string) rather than `scp` (array).

## Gotchas

- Do not mix `ROLE_` conventions and `SCOPE_` conventions accidentally.
- Do not use `authorities-claim-expressions` together with `authorities-claim-name` or `authorities-claim-delimiter`.
  - They are mutually exclusive.
- Do not assume a third-party issuer uses `scope` or `scp`.
- Do not leave audience validation implicit when downstream authorization depends on token audience.
- Do not disable `exp` or `iss` validation in production unless the deployment model genuinely requires it.
- Do not assume the principal name is `sub` for third-party issuers.
  - Many use `preferred_username` or `email`.
