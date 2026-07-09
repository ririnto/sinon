# Spring CredHub auth variants

Open this reference when the ordinary mutual-TLS password-read path in [`SKILL.md`](../SKILL.md) is not enough and the blocker is choosing between mutual TLS and OAuth2 client authentication.

The current stable Spring CredHub line is 4.0.1, and the official reference documents both mutual TLS and OAuth2 client-auth paths.

## Mutual TLS versus OAuth2

Choose the authentication mode that the platform already supports.

- Prefer mutual TLS when client certificates are already provisioned and renewed by the platform.
- Use OAuth2 only when the platform already exposes a compatible token flow for CredHub clients and client certificates are not the standard path.
- Keep one auth mode active per application profile so startup failures stay attributable to one certificate or token boundary.

## Mutual TLS configuration shape

The CredHub starter exposes no `spring.credhub.tls.*` namespace.
It binds only `spring.credhub.url`, `spring.credhub.oauth2.registration-id`, and the `ClientOptions` fields (`connection-timeout`, `read-timeout`, `ca-cert-files`).

For CA trust (validating the CredHub server certificate), use the `ca-cert-files` property.

```yaml
spring:
  credhub:
    url: https://credhub.example.com:8844
    ca-cert-files:
      - file:/etc/credhub/credhub-ca.pem
```

For client-certificate mutual TLS, the starter has no property and `ClientOptions` carries no key material.
Supply a custom `CredHubOperations` bean; the auto-configured bean backs off with `@ConditionalOnMissingBean`.
Build it from a `CredHubTemplate(CredHubProperties, ClientHttpRequestFactory)` whose request factory carries the client key material.

```java
@Configuration
class CredHubMutualTlsConfiguration {
    /**
     * Provide a CredHub client that presents a client certificate.
     *
     * @param properties the bound CredHub properties carrying the configured URL.
     * @param requestFactory a request factory preloaded with the client key material.
     * @return a CredHubOperations backed by a mutual-TLS request factory.
     */
    @Bean
    CredHubOperations credHubOperations(CredHubProperties properties, ClientHttpRequestFactory requestFactory) {
        return new CredHubTemplate(properties, requestFactory);
    }
}
```

Obtain the mutual-TLS `ClientHttpRequestFactory` from the surrounding platform's SSL configuration; the CredHub library provides no client-certificate helper.

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
OAuth2AuthorizedClientManager credHubAuthorizedClientManager(ClientRegistrationRepository registrations, OAuth2AuthorizedClientService authorizedClients) {
    return new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, authorizedClients);
}
```

Use this path only when the default request-bound OAuth2 client flow cannot supply tokens for the CredHub client boundary.

## Gotchas

- Do not configure mutual TLS and OAuth2 in the same profile and expect the application to choose safely at runtime.
- When OAuth2 token acquisition happens outside an `HttpServletRequest`, the default request-bound manager is the wrong boundary even if ordinary web requests succeed.
- When mutual TLS fails at startup, verify keystore and truststore material before assuming the CredHub server is unavailable.

## Decision points

| Situation | First check |
| --- | --- |
| Platform issues client certificates | prefer mutual TLS |
| Platform standardizes on OAuth2 tokens | use OAuth2 client auth |
| Startup auth behavior is inconsistent | verify only one auth mode is active per profile |

## Related blockers

- Return to [`SKILL.md`](../SKILL.md) when the blocker is the ordinary service-boundary read or write path.
- Open [`credhub-reactive-access.md`](credhub-reactive-access.md) when the actual blocker is a fully reactive client boundary.
- Open [`credhub-advanced-credential-patterns.md`](credhub-advanced-credential-patterns.md) when the actual blocker is interpolation, certificate generation, or non-default credential families.
