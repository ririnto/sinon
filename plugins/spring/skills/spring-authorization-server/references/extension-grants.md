# Extension grants: custom grant type implementation

Open this reference when implementing a custom grant type beyond the standard authorization code, refresh token, client credentials, or device-code flows.

## When to open this reference

- The task requires a grant type that is not supported by the built-in flow set.
- A protocol needs token exchange or an extension flow that Spring Authorization Server does not ship out of the box.
- An existing custom grant needs a converter or provider seam.

## Custom grant seam

Spring Authorization Server exposes two primary seams for custom grants:

- `AuthenticationConverter` — converts an incoming HTTP request into a grant-specific authentication token.
- `AuthenticationProvider` — validates the custom grant and issues the token.

Those seams are paired with token generation and authorization persistence.

## AuthenticationConverter pattern

```java
public final class CustomCodeGrantAuthenticationConverter implements AuthenticationConverter {
    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!"urn:ietf:params:oauth:grant-type:custom_code".equals(grantType)) {
            return null;
        }
        return new CustomCodeGrantAuthenticationToken(request.getParameter("code"), SecurityContextHolder.getContext().getAuthentication(), additionalParameters);
    }
}
```

The converter returns a grant-specific authentication token that the matching provider then processes.

Register it in the token endpoint:

```java
CustomCodeGrantAuthenticationConverter converter = new CustomCodeGrantAuthenticationConverter();

http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
    .tokenEndpoint(token -> token
        .accessTokenRequestConverter(converter)
    );
```

## AuthenticationProvider pattern

```java
@Component
public final class CustomCodeGrantAuthenticationProvider implements AuthenticationProvider {
    private final RegisteredClientRepository clients;
    private final OAuth2AuthorizationService authorizations;
    private final OAuth2TokenGenerator<?> tokenGenerator;

    @Override
    public Authentication authenticate(Authentication authentication) {
        CustomCodeGrantAuthenticationToken customGrantAuthentication = (CustomCodeGrantAuthenticationToken) authentication;
        OAuth2ClientAuthenticationToken clientPrincipal = getAuthenticatedClientElseThrowInvalidClient(customGrantAuthentication);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        OAuth2TokenContext tokenContext = DefaultOAuth2TokenContext.builder()
            .registeredClient(registeredClient)
            .principal(clientPrincipal)
            .authorizationServerContext(AuthorizationServerContextHolder.getContext())
            .authorizationGrantType(new AuthorizationGrantType("urn:ietf:params:oauth:grant-type:custom_code"))
            .authorizationGrant(customGrantAuthentication)
            .tokenType(OAuth2TokenType.ACCESS_TOKEN)
            .build();
        OAuth2Token generatedAccessToken = tokenGenerator.generate(tokenContext);
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
            .principalName(clientPrincipal.getName())
            .authorizationGrantType(new AuthorizationGrantType("urn:ietf:params:oauth:grant-type:custom_code"))
            .token((OAuth2AccessToken) generatedAccessToken)
            .build();
        authorizations.save(authorization);
        return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, (OAuth2AccessToken) generatedAccessToken);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return CustomCodeGrantAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
```

## Decision rules

| Condition | Decision |
| --- | --- |
| Standard grant covers the protocol need | Use the built-in grant. Do not add a custom one |
| Protocol requires a new `grant_type` URI | Implement both converter and provider |
| Custom grant must survive restarts | Use a persistent `OAuth2AuthorizationService` |

## Error handling for custom grants

Return errors from the provider by throwing `OAuth2AuthenticationException` so the framework serializes the standard token error response:

```java
throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT));
```

## Testing custom grants

Test the converter and provider separately from the integration:

```java
@Test
void converterReturnsEmptyWhenGrantTypeUnknown() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("grant_type", "unknown_grant");
    AuthenticationConverter converter = new CustomCodeGrantAuthenticationConverter();
    Authentication result = converter.convert(request);
    assertNull(result);
}

@Test
void authenticateRejectsUnknownClient() {
    when(clients.findByClientId("unknown")).thenReturn(null);
    AuthenticationProvider provider = new CustomCodeGrantAuthenticationProvider(clients, auths, generator);
    Authentication authentication = new CustomCodeGrantAuthenticationToken("code", principal, Map.of());
    assertThrowsExactly(OAuth2AuthenticationException.class, () -> provider.authenticate(authentication));
}
```

Run integration tests against the actual `/oauth2/token` endpoint with a client provisioned for the custom grant type.

## Official documentation

- [How-to: Implement an extension authorization grant type](https://docs.spring.io/spring-authorization-server/reference/guides/how-to-ext-grant-type.html)
- [Protocol endpoints](https://docs.spring.io/spring-authorization-server/reference/protocol-endpoints.html)
