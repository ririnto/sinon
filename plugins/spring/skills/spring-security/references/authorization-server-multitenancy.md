# Spring Authorization Server multitenancy

Open this reference when the task requires a single authorization server to serve multiple issuers, issuer-scoped client registrations, or issuer-scoped signing keys.

## Official multitenancy posture

The official Spring Authorization Server model is multiple issuers per host. The issuer path component acts as the tenant discriminator.

Enable it like this:

```java
AuthorizationServerSettings settings = AuthorizationServerSettings.builder()
    .multipleIssuersAllowed(true)
    .build();
```

Do not call `.issuer(...)` in this mode. An explicit issuer forces single-tenant behavior.

## Issuer-based component delegation

Delegate these components by issuer:

- `RegisteredClientRepository`
- `OAuth2AuthorizationService`
- `OAuth2AuthorizationConsentService`
- `JWKSource<SecurityContext>`

### Tenant-per-issuer registry shape

```java
public final class TenantPerIssuerComponentRegistry {
    private final ConcurrentMap<String, Map<Class<?>, Object>> registry = new ConcurrentHashMap<>();

    public <T> T get(Class<T> componentType) {
        AuthorizationServerContext context = AuthorizationServerContextHolder.getContext();
        String issuer = context.getIssuer();
        Map<Class<?>, Object> components = registry.get(issuer);
        return componentType.cast(components.get(componentType));
    }
}
```

### RegisteredClientRepository isolation

```java
class TenantRegisteredClientRepository implements RegisteredClientRepository {
    private final TenantPerIssuerComponentRegistry componentRegistry;

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return componentRegistry.get(RegisteredClientRepository.class).findByClientId(clientId);
    }
}
```

### OAuth2AuthorizationService isolation

```java
class TenantOAuth2AuthorizationService implements OAuth2AuthorizationService {
    private final TenantPerIssuerComponentRegistry componentRegistry;

    @Override
    public void save(OAuth2Authorization authorization) {
        componentRegistry.get(OAuth2AuthorizationService.class).save(authorization);
    }
}
```

## Issuer-scoped key management

Signing keys must be scoped per issuer when tenants have distinct key material.

```java
class TenantJWKSource implements JWKSource<SecurityContext> {
    private final TenantPerIssuerComponentRegistry componentRegistry;

    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext securityContext) throws KeySourceException {
        return componentRegistry.get(JWKSource.class).get(selector, securityContext);
    }
}
```

Keep database and Redis storage strategies in their own persistence references. This file is about issuer-scoped component delegation, not storage modeling.

## Decision points

| Situation | Choice |
| --- | --- |
| one host serves multiple issuers | `multipleIssuersAllowed(true)` |
| signing keys are shared across tenants | single `JWKSource` |
| each tenant has distinct key material | per-issuer delegated `JWKSource` |
| client lookup must be issuer-scoped | delegating repository through issuer registry |

## Official documentation

- [Configuration model](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/configuration-model.html)
