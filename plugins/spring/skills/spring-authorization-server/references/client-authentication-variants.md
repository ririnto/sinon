# Client authentication variants

Open this reference when the common-path public-client PKCE baseline in [SKILL.md](../SKILL.md) is not enough and the blocker is client type or token-endpoint authentication method selection.

## When to open

- Client authentication fails and the method configuration is unclear.
- Public client vs confidential client tradeoffs block a registration decision.
- PKCE must be required, optional, or disabled and the interaction with client type is not obvious.
- A custom authentication mechanism must be plugged in.

## Client type boundary

**Confidential clients** can hold credentials securely. They commonly use `client_secret_post`, `client_secret_basic`, `client_secret_jwt`, or `private_key_jwt` to authenticate at the token endpoint.

**Public clients** cannot hold credentials. They typically use `ClientAuthenticationMethod.NONE`, and PKCE is the primary protection against authorization-code interception.

```text
requireProofKey(true)   -> PKCE is mandatory, authorization code exchange fails without it
requireProofKey(false)  -> PKCE is optional but recommended for public clients
```

## Authentication method registration

Register the method on `RegisteredClient.Builder` and keep PKCE in `ClientSettings`:

```java
RegisteredClient.withId(UUID.randomUUID().toString())
    .clientId("messaging-client")
    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
    .clientSettings(ClientSettings.builder().requireProofKey(true).build())
    .build();
```

This example requires PKCE while keeping the token-endpoint authentication method on the registered client itself.

Supported `ClientAuthenticationMethod` values:

| Method | When to use |
| --- | --- |
| `CLIENT_SECRET_BASIC` | Confidential web servers using HTTP Basic over TLS |
| `CLIENT_SECRET_POST` | Confidential form-post clients |
| `CLIENT_SECRET_JWT` | Confidential servers that can sign a JWT assertion |
| `PRIVATE_KEY_JWT` | Clients that authenticate with an asymmetric key pair |
| `TLS_CLIENT_AUTH` | Confidential clients authenticated by a CA-issued client certificate |
| `SELF_SIGNED_TLS_CLIENT_AUTH` | Confidential clients authenticated by a self-signed certificate and JWK Set |
| `NONE` | Public clients; acceptable with PKCE and no shared secret |

## PKCE and client type interaction

PKCE is not an authentication method. It protects the authorization-code exchange regardless of client type.

For public clients:

- Use `requireProofKey(true)` as the baseline.
- Use `ClientAuthenticationMethod.NONE`.
- Decide consent separately based on product requirements and client trust, not on public-client status alone.

For confidential clients:

- PKCE is still valid and often worth keeping even when a client secret exists.
- Do not disable PKCE unless the client and threat model are both fully understood.

## Custom authentication method

Implement an application-owned authentication converter and register it through the official client-authentication configurer:

```java
public class CustomClientAuthenticationConverter implements AuthenticationConverter {
    @Override
    public Authentication convert(HttpServletRequest request) {
        return new OAuth2ClientAuthenticationToken(clientId, authenticationMethod, clientSecret, Collections.emptyMap());
    }
}
```

Extract and validate the custom client-authentication data before constructing the authentication token. This converter is application-owned glue, not a special built-in framework SPI beyond the standard authentication-conversion seam.

Register via `OAuth2AuthorizationServerConfigurer`:

```java
http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
    .clientAuthentication(clientAuth -> clientAuth.authenticationConverter(new CustomClientAuthenticationConverter()));
```

## Official references

- [Configuration model](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/configuration-model.html)
- [Core model components](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/core-model-components.html)
- [Authorization code grant and PKCE](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/core-model-components.html)
