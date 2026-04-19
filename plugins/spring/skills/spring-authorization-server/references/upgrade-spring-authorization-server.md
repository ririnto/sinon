# Upgrading Spring Authorization Server

Open this reference when the task involves upgrading Spring Authorization Server itself or moving from a legacy Spring Security OAuth server to Spring Authorization Server.

## Authorization-server upgrade blocker

**Problem:** a version upgrade changes endpoint behavior, JWK handling, claims, or redirect compatibility.

**Solution:** treat the dependency upgrade as a compatibility review across issuer, keys, claims, and registered-client data.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
    <version>1.6.0</version>
</dependency>
```

## Migration branches

- Regenerate or rotate JWK material only when the upgraded version changes key handling or `kid` expectations.
- Re-check redirect URIs and issuer URLs together when domains or endpoint paths change.
- Revisit custom claims and token settings when clients depend on claim presence or lifetime semantics.
- Move legacy Spring Security OAuth server behavior behind explicit Spring Authorization Server components instead of carrying old abstractions forward implicitly.

## Decision points

| Situation | First check |
| --- | --- |
| Upgrade branch fails to start | verify the target authorization-server version and its Spring Security baseline |
| Resource servers reject new tokens | verify JWK material, `kid`, and claim compatibility |
| Clients fail discovery after upgrade | verify issuer URL, metadata endpoint, and redirect URI compatibility |

## Pitfalls

- Do not upgrade the dependency and skip endpoint or token-compatibility tests.
- Do not remove old signing keys before tokens signed with them have expired.
- Do not mix legacy Spring Security OAuth assumptions into the new component model without an explicit mapping plan.
