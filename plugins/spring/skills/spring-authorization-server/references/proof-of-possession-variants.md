# Proof-of-possession variants

Open this reference when bearer tokens are not enough and the blocker is DPoP or MTLS-bound access-token behavior.

## DPoP blocker

DPoP (RFC 9449) binds an access token to a public key. Open this blocker only when access tokens must not remain usable after bearer-header extraction.

```java
TokenSettings.builder()
    .accessTokenFormat(OAuth2TokenFormat.JWT)
    .build();
```

This keeps JWT access tokens enabled because DPoP relies on proof-bound token validation.

Private-key JWT (`PRIVATE_KEY_JWT`) authentication and DPoP are separate mechanisms. A client can use `PRIVATE_KEY_JWT` for token-endpoint authentication while also presenting a DPoP proof when using the access token.

## MTLS blocker

MTLS (RFC 8705) binds tokens to a client certificate presented at the TLS layer. This is enforced by the TLS termination point, not by Spring Authorization Server directly.

When the authorization server sits behind a TLS-terminating proxy, configure the proxy to pass client certificate information:

```java
.http().authorizeHttpRequests(authorize -> authorize
    .requestMatchers("/token").authenticated()
)
.x509(x509 -> x509
    .subjectPrincipalRegex("CN=(.*?)(?:,|$)")
)
```

Certificate-bound refresh tokens require the `cct` claim. This is set automatically when MTLS is detected and the token format is JWT.

## Official documentation

- [DPoP](https://docs.spring.io/spring-authorization-server/reference/grant-authorization-code.html#access-token-dpop)
- [MTLS and Certificate-Bound Tokens](https://docs.spring.io/spring-authorization-server/reference/grant-authorization-code.html#mutual-tls-and-certificate-bound-access-tokens)
