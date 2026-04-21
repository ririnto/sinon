# Proof-of-possession variants

Open this reference when bearer tokens are not enough and the blocker is DPoP or MTLS-bound access-token behavior.

## DPoP blocker

DPoP (RFC 9449) binds an access token to a public key. Open this blocker only when access tokens must not remain usable after bearer-header extraction.

```java
TokenSettings.builder()
    .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
    .build();
```

This keeps JWT access tokens enabled because DPoP relies on proof-bound token validation.

Private-key JWT (`PRIVATE_KEY_JWT`) authentication and DPoP are separate mechanisms. A client can use `PRIVATE_KEY_JWT` for token-endpoint authentication while also presenting a DPoP proof when using the access token.

## MTLS blocker

MTLS (RFC 8705) uses a client certificate during client authentication and can bind an access token to that certificate. The TLS termination point still must pass the client-certificate information correctly, but Spring Authorization Server also needs MTLS-aware client registration and token settings.

When the authorization server sits behind a TLS-terminating proxy, treat certificate forwarding as transport prerequisite work first. Then configure the registered client for MTLS at the authorization-server layer.

Use MTLS client authentication on the registered client and enable certificate-bound access tokens explicitly:

```java
RegisteredClient mtlsClient = RegisteredClient.withId(UUID.randomUUID().toString())
    .clientId("mtls-client")
    .clientAuthenticationMethod(ClientAuthenticationMethod.TLS_CLIENT_AUTH)
    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
    .clientSettings(ClientSettings.builder().x509CertificateSubjectDN("CN=mtls-client").build())
    .tokenSettings(TokenSettings.builder().x509CertificateBoundAccessTokens(true).build())
    .build();
```

For self-signed MTLS clients, use `ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH` and register the client JWK Set instead of a subject-DN matcher.

The resulting JWT access token uses the certificate thumbprint confirmation claim (`cnf.x5t#S256`).

## Official documentation

- [Protocol endpoints](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/protocol-endpoints.html)
- [Configuration model](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/configuration-model.html)
