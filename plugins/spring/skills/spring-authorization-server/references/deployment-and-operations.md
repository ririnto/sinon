# Spring Authorization Server deployment and operations

Open this reference when the task involves production hardening, issuer stability, signing key protection, proxy configuration, or operational concerns for a running authorization server.

## When to open this file

Open this reference when the task involves:

- Production environment configuration for the authorization server
- Issuer URL stability and domain setup
- JWK key material generation, storage, and rotation
- Reverse proxy or load balancer configuration in front of the server
- Token expiration tuning and refresh strategy
- Health endpoints and operational monitoring
- TLS termination and secure cookie settings
- Restart continuity or clustered deployment is blocked by persistence choices already modeled in [jpa-persistence.md](jpa-persistence.md) or [redis-persistence.md](redis-persistence.md)

## Issuer URL stability

The issuer URL must be stable and externally consistent. Clients resolve the authorization server by its issuer identifier.

### Issuer URL configuration

```yaml
spring:
  security:
    oauth2:
      authorizationserver:
        issuer: https://auth.example.com
```

The issuer URL must match exactly what clients use to discover the provider, including scheme and trailing slash behavior.

### Trailing slash rule

If clients use `https://auth.example.com` without a trailing slash, the server must present the same URL. Adding or removing a trailing slash breaks client discovery.

## Key protection

### In-memory JWK source for single instance

```java
@Bean
JWKSource<SecurityContext> jwkSource() {
    RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
        .privateKey(keyPair.getPrivate())
        .keyID("primary")
        .build();
    return new ImmutableJWKSet(new JWKSet(rsaKey));
}
```

This works for single-instance deployments only. Multiple instances must share key material through an external store.

### Shared key source for multi-instance deployments

Multiple instances must share key material through an external store or a custom `JWKSource<SecurityContext>` implementation.

```java
@Bean
JWKSource<SecurityContext> jwkSource() {
    return loadSharedJwkSourceFromExternalStore();
}
```

The official requirement is shared key material, not a specific built-in JDBC JWK class.

## Key rotation

### Key rotation procedure

1. Generate a new RSA key with a distinct `kid`.
2. Publish the new key to the JWK endpoint alongside the existing key.
3. Allow time for clients to fetch and cache the new key.
4. Stop using the old key for new token signatures.
5. Remove the old key from the JWK endpoint after all tokens signed with it have expired.

### Rotation configuration

```java
@Bean
JWKSource<SecurityContext> jwkSource() {
    RSAKey currentKey = generateRsa("current-key-id");
    RSAKey nextKey = generateRsa("next-key-id");

    JWKSet jwkSet = new JWKSet(List.of(currentKey, nextKey));
    return (selector, context) -> selector.select(jwkSet);
}
```

Keep the old key active until the rotation window closes. The `kid` in the token header tells clients which key to use.

## Reverse proxy concerns

When the authorization server sits behind a reverse proxy or load balancer:

### Trust the proxy for scheme and host

```yaml
server:
  forward-headers-strategy: native
```

Use `forward-headers-strategy: native` when the runtime or proxy already provides standard forwarded-header support. Switch to `forward-headers-strategy: framework` only when native support is not enough for the deployment.

### Required forwarded headers

Configure the proxy to send:

- `X-Forwarded-Host` — the original host clients see
- `X-Forwarded-Proto` — `http` or `https` as seen by clients
- `X-Forwarded-Port` — the original port clients use

### Authorization server behind path-based routing

If the proxy routes `/auth-server/**` to the authorization server, the issuer URL must include the path prefix:

```yaml
spring:
  security:
    oauth2:
      authorizationserver:
        issuer: https://gateway.example.com/auth-server
```

Clients must use the same path-based issuer URL.

## Deployment hardening

### HTTPS only

```yaml
server:
  ssl:
    enabled: true
```

Require HTTPS in production. The authorization endpoint redirects clients; an unencrypted channel exposes the authorization code to interception.

### Secure cookie settings

When session cookies are used for the authorization server login:

```yaml
server:
  servlet:
    session:
      cookie:
        secure: true
        http-only: true
        same-site: strict
```

### Token endpoint security

The token endpoint should accept only POST with form-encoded bodies. Ensure the security filter chain does not permit GET requests to `/oauth2/token`.

## Operational endpoints

### Health check

```java
@RestController
class HealthController {
    @GetMapping("/actuator/health/auth-server")
    Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
```

### Token introspection and UserInfo operational check

The introspection endpoint and OIDC UserInfo endpoint are part of the provider contract. Verify that they are enabled only when needed and exercised in deployment tests.

- Introspection matters when relying parties or resource servers need remote token liveness checks.
- OIDC UserInfo becomes active when OIDC support is enabled.
- Follow the official OIDC configuration path when enabling UserInfo; do not assume a separate resource-server chain is always required by hand.

## Production checklist

- [ ] Issuer URL matches what clients use for discovery
- [ ] HTTPS is required; no HTTP fallback in production
- [ ] JWK key material is backed up and rotation is documented
- [ ] Forwarded headers are configured on the proxy and enabled in the server
- [ ] Authorization and consent state are persisted when restart continuity is required
- [ ] Session cookies use `secure`, `http-only`, and `same-site` settings
- [ ] Token expiration values match the expected client usage patterns
- [ ] Health endpoint returns 200 when the server is ready

## Decision points

| Situation | First check |
| --- | --- |
| Clients fail issuer discovery | verify issuer URL matches exactly including scheme and path |
| Tokens signed with different key than JWKS shows | confirm key rotation did not remove the active signing key |
| Redirect URLs use wrong host | check `X-Forwarded-Host` is sent by the proxy and forwarded-header handling is enabled, using `native` first and `framework` only when native support is insufficient |
| Multiple instances produce different JWK sets | use a shared key store or custom shared `JWKSource` instead of per-node in-memory keys |
| Authorization state lost after restart | see [jpa-persistence.md](jpa-persistence.md) or [redis-persistence.md](redis-persistence.md) |
