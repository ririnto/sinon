# Spring CredHub auth variants

Open this reference when the ordinary mutual-TLS password-read path in [SKILL.md](../SKILL.md) is not enough and the blocker is choosing between mutual TLS and OAuth2 client authentication.

## Mutual TLS versus OAuth2

Choose the authentication mode that the platform already supports.

- Prefer mutual TLS when client certificates are already provisioned and renewed by the platform.
- Use OAuth2 only when the platform already exposes a compatible token flow for CredHub clients and client certificates are not the standard path.

## Mutual TLS configuration shape

```yaml
spring:
  credhub:
    url: https://credhub.example.com:8844
    tls:
      enabled: true
      key-store: classpath:credhub-client.p12
      key-store-password: ${CREDHUB_KEYSTORE_PASSWORD}
      trust-store: classpath:credhub-truststore.p12
      trust-store-password: ${CREDHUB_TRUSTSTORE_PASSWORD}
```

## OAuth2 configuration shape

```yaml
spring:
  credhub:
    url: https://credhub.example.com:8844
    oauth2:
      registration-id: credhub-client
```

Keep the OAuth2 client registration outside the skill-specific service layer so token acquisition stays a platform concern.

## OAuth2 manager caveat

When CredHub access happens in startup logic or service code outside an `HttpServletRequest`, do not rely on a request-scoped authorized-client manager.

```java
@Bean
OAuth2AuthorizedClientManager credHubAuthorizedClientManager(
    ClientRegistrationRepository registrations,
    OAuth2AuthorizedClientService authorizedClients
) {
    return new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
}
```

Use this path only when the default request-bound OAuth2 client flow cannot supply tokens for the CredHub client boundary.

## Decision points

| Situation | First check |
| --- | --- |
| Platform issues client certificates | prefer mutual TLS |
| Platform standardizes on OAuth2 tokens | use OAuth2 client auth |
| Startup auth behavior is inconsistent | verify only one auth mode is active per profile |
