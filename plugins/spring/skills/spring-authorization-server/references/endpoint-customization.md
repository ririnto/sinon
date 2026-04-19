# Endpoint customization

Open this reference when the task needs to change how official authorization-server endpoints validate requests, convert inputs, or shape success and error handling beyond the defaults.

## Official endpoint configurers

Spring Authorization Server exposes endpoint customization through `OAuth2AuthorizationServerConfigurer`, not by replacing filters.

- `authorizationEndpoint(...)`
- `pushedAuthorizationRequestEndpoint(...)`
- `deviceAuthorizationEndpoint(...)`
- `deviceVerificationEndpoint(...)`
- `tokenEndpoint(...)`
- `tokenIntrospectionEndpoint(...)`
- `tokenRevocationEndpoint(...)`
- `authorizationServerMetadataEndpoint(...)`
- `oidc(oidc -> oidc.providerConfigurationEndpoint(...).logoutEndpoint(...).userInfoEndpoint(...).clientRegistrationEndpoint(...))`

## Token endpoint customization

The token endpoint uses `AuthenticationConverter` and `AuthenticationProvider` chains.

```java
http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
    .tokenEndpoint(token -> token
        .accessTokenRequestConverter(this::configureAccessTokenRequestConverters)
        .authenticationProvider(this::configureAuthenticationProvider)
        .accessTokenResponseHandler(this::handleAccessTokenResponse)
        .errorResponseHandler(this::handleErrorResponse)
    );
```

## Authorization-request validator hook

For request validation, prefer the official provider validator hooks rather than inventing a token-endpoint validator abstraction.

```java
Consumer<List<AuthenticationProvider>> configureAuthenticationProviders = authenticationProviders ->
    authenticationProviders.forEach(authenticationProvider -> {
        if (authenticationProvider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider authorizationCodeRequestAuthenticationProvider) {
            authorizationCodeRequestAuthenticationProvider.setAuthenticationValidator(customValidator);
        }
    });
```

## Authorization endpoint customization

```java
http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
    .authorizationEndpoint(authorization -> authorization
        .consentPage("/custom-consent")
        .authenticationConverter(this::configureAuthenticationConverter)
    );
```

## Decision rules

| Situation | Use |
| --- | --- |
| Need to add or replace a grant type handler | Add `AuthenticationConverter` and `AuthenticationProvider`, not a new endpoint |
| Need to validate extra authorization request parameters | Set the official authentication validator on the matching provider |
| Need a custom JSON structure in token response | Use the endpoint success handler seam |
| Need extra fields in error body | Use the endpoint error handler seam |
| Need per-endpoint behavior change | Use the specific endpoint configurer, not global filters |

## Request validation rules

- Use `RegisteredClientRepository` checks for client-level validation.
- Use the official provider validator hooks for request-level validation where the framework exposes them.
- Use `AuthenticationProvider` for protocol-level validation during authentication.
- Do not duplicate client checks across multiple layers.

## Official documentation

- [Configuration model](https://docs.spring.io/spring-authorization-server/reference/configuration-model.html)
- [Protocol endpoints](https://docs.spring.io/spring-authorization-server/reference/protocol-endpoints.html)
