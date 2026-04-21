# Spring Security servlet token introspection resource server

Open this reference when the application acts as a servlet resource server and the token-validation contract is introspection instead of local JWT verification.

Use this path only when the authorization server contract requires an introspection call.

## Introspection versus local JWT verification

| Validation contract | What happens | When to use |
| --- | --- | --- |
| Local JWT verification | Validate the token locally against issuer metadata or a trusted public key | Issuer provides signed JWTs and introspection is not required |
| Token introspection | Call the authorization server for token validation and attributes | The server contract requires introspection for each bearer token |

Prefer local JWT validation when introspection is not required, because it is cheaper and stateless.

## Dependency

These dependency examples assume Spring Boot dependency management (parent/BOM) is already in use, so the managed Spring versions stay versionless here.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

Opaque token support ships in the same starter as JWT resource server. No additional dependency is required.

## Minimal opaque-token servlet configuration

```java
@Configuration
@EnableMethodSecurity
class OpaqueTokenSecurityConfig {
    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/actuator/health"))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2.opaqueToken(Customizer.withDefaults()))
            .build();
    }
}
```

Use `securityMatcher(...)` here only when another explicit fallback chain protects the rest of the application. If this is the only chain, leave chain selection broad and scope access with `authorizeHttpRequests(...)`.

Spring Boot can also configure the introspection endpoint directly:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        opaquetoken:
          introspection-uri: https://idp.example.com/introspect
          client-id: client
          client-secret: secret
```

## Customizing introspection behavior

Use `OpaqueTokenIntrospector` to control how the opaque token is validated and how claims are extracted.

```java
@Bean
OpaqueTokenIntrospector introspector() {
    return SpringOpaqueTokenIntrospector.withIntrospectionUri("https://issuer.example.com/oauth2/introspect")
        .clientId("client-id")
        .clientSecret("client-secret")
        .build();
}
```

Wire it into the DSL:

```java
.oauth2ResourceServer(oauth2 -> oauth2.opaqueToken(opaque -> opaque.introspector(introspector())))
```

## Decision points

| Situation | Use |
| --- | --- |
| Contract requires local JWT verification | JWT resource server, not this path |
| Contract requires token introspection | this introspection path |
| Introspection endpoint requires client credentials | `SpringOpaqueTokenIntrospector` with client credentials |
| Custom claim extraction from introspection response | custom `OAuth2AuthenticatedPrincipal` mapping |

## Gotchas

- Do not mix opaque token and JWT configuration in the same chain without clear path separation.
- Do not assume opaque token introspection is cheap; it is a network call on every request.
- Do not leave the introspection endpoint unauthenticated in production unless the issuer allows it.
