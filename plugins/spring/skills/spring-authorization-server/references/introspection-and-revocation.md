# Introspection and revocation

Open this reference when the ordinary issuer, authorization, token, metadata, and JWK endpoint set in [SKILL.md](../SKILL.md) is not enough and the blocker is token liveness inspection or revocation.

## Introspection blocker

**Problem:** a relying party or resource server must ask the authorization server whether an opaque or reference-style token is still active.

**Solution:** expose and test the token introspection endpoint as a server-to-server contract.

```text
introspection: /oauth2/introspect
```

Use introspection when token validity cannot be decided from local signature verification alone.

## Revocation blocker

**Problem:** clients must explicitly invalidate an issued token before natural expiration.

**Solution:** expose and test the token revocation endpoint as a deliberate client capability.

```text
revocation: /oauth2/revoke
```

Revocation changes token lifecycle semantics. Treat it as part of the client compatibility surface.

## Endpoint customization seam

```java
http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
    .tokenIntrospectionEndpoint(introspection -> {
    })
    .tokenRevocationEndpoint(revocation -> {
    });
```

## Decision points

| Situation | First choice |
| --- | --- |
| Resource server uses opaque or reference tokens | introspection endpoint |
| Client must invalidate tokens on logout or device loss | revocation endpoint |
| Self-contained JWTs are sufficient and no remote liveness checks are needed | ordinary path without introspection |

## Pitfalls

- Do not assume JWT signature validation replaces every introspection use case.
- Do not add revocation without deciding which clients are allowed to call it and how the result is verified.
- Do not enable introspection or revocation and skip testing one successful and one rejected request path.
