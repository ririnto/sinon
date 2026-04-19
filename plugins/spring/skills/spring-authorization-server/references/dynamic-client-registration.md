# Dynamic client registration

Open this reference when the provider must allow external clients to self-register instead of being provisioned explicitly in application configuration.

## Dynamic registration rule

Enable the registration endpoint only when external clients need self-service onboarding and the security model is already defined.

```java
http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
    .oidc(oidc -> oidc.clientRegistrationEndpoint(Customizer.withDefaults()));
```

This exposes `POST /connect/register`.

## Initial access token blocker

Require an initial access token to gate who can register clients.

```java
@Configuration
class ClientRegistrationConfig {
    @Bean
    RegisteredClientRepository registeredClientRepository() {
        RegisteredClient initialAccessClient = RegisteredClient.withId("initial-access")
            .clientId("initial-access-client")
            .clientSecret("{noop}initial-access-secret")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("client.create")
            .build();
        return new InMemoryRegisteredClientRepository(initialAccessClient);
    }
}
```

Use the initial-access token only for registration. The official path is initial-access-token based.

## Custom metadata converters

When client metadata needs custom mapping, customize the official registration providers instead of inventing a separate validator seam.

```java
OidcClientRegistrationAuthenticationProvider registrationProvider = new OidcClientRegistrationAuthenticationProvider(authorizationService, registeredClientRepository);
registrationProvider.setRegisteredClientConverter(customRegisteredClientConverter);
registrationProvider.setClientRegistrationConverter(customClientRegistrationConverter);
```

## Decision table

| Situation | Use |
| --- | --- |
| External clients self-register | Enable DCR endpoint and gate it with an initial access token |
| Custom client metadata must round-trip | Customize the official registration converters on the registration providers |

## Official documentation

- [Dynamic Client Registration](https://docs.spring.io/spring-authorization-server/reference/guides/how-to-dynamic-client-registration.html)
