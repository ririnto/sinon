# Federated identity and social login

Open this reference when the authorization server must authenticate users through an external identity provider instead of a local login form.
This reference is about the provider side of federation, not about an ordinary application that only consumes an external provider as an OAuth2 client.
The outbound OAuth2 client-only login case is outside this skill.
Federation remains in scope when the same application authenticates users externally and then issues its own authorization-server tokens.

## Federated identity boundary

Federated identity applies when the authorization server delegates user authentication to an external provider rather than authenticating the user from a local user store.

This is distinct from a normal Spring Security OAuth2 client application.
Here the authorization server still issues tokens.
Only the user-authentication source changes.

## Official federation posture

The official Spring Authorization Server pattern is modest: in the non-authorization-server login chain, use either `formLogin()` for local user authentication or `oauth2Login()` for external-provider authentication, then bridge the authenticated identity into the authorization-server flow.
Do not replace or add either login mechanism in the authorization-server protocol chain itself.
`oauth2Login()` here is the user-authentication boundary, not outbound OAuth2 client-only application behavior.

### Non-authorization-server login chain

```java
@Bean
@Order(2)
SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .oauth2Login(Customizer.withDefaults())
        .build();
}
```

### Redirect unauthenticated requests to the federated login entry point

```java
http.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/my-client"), new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
```

### Success handler bridge

Use an application-defined success handler wired into the normal Spring Security login flow to provision or update the local user view after external login succeeds.

```java
AuthenticationSuccessHandler successHandler = new FederatedIdentityAuthenticationSuccessHandler();
```

### ID-token customization bridge

Use an application-defined `OAuth2TokenCustomizer<JwtEncodingContext>` when identity from the external provider must be reflected into issued ID tokens.

```java
OAuth2TokenCustomizer<JwtEncodingContext> idTokenCustomizer = new FederatedIdentityIdTokenCustomizer();
```

`FederatedIdentityAuthenticationSuccessHandler` and `FederatedIdentityIdTokenCustomizer` here are local example helpers, not built-in Spring Authorization Server framework types.
Keep deeper account-linking, identity normalization, and logout choreography as application-specific work.

## Decision table

| Situation | Use |
| --- | --- |
| External OIDC provider is the login source | Use `oauth2Login()` in the non-AS chain |
| Unauthenticated HTML requests should land at the external provider | Use `LoginUrlAuthenticationEntryPoint` for `/oauth2/authorization/{registrationId}` |
| External identity must affect issued ID tokens | Use a federated ID-token customizer |

## Official documentation

- [Configuration model](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/configuration-model.html)
