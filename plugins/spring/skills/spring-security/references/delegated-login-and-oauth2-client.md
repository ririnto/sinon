# Spring Security delegated login and OAuth2 client

Open this reference when the ordinary servlet path in `SKILL.md` is not enough and the task depends on delegated login through an external identity provider or outbound OAuth2 client token management.

Keep provider registration, redirect URIs, and granted scopes explicit.

## Delegated login baseline

Use OAuth2 login when the application delegates user authentication to an external identity provider and then creates a local authenticated session.

When these examples use Spring Boot starters, they assume Boot dependency management/BOM is already providing the Spring Security versions.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

### Configuration properties shape

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: google-client-id
            client-secret: google-client-secret
            scope: openid,profile,email
```

### Security filter chain shape

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2Login(Customizer.withDefaults())
        .build();
}
```

## Programmatic client registration

Use this shape when registration data does not live only in configuration files.

```java
@Bean
ClientRegistrationRepository clientRegistrationRepository() {
    ClientRegistration registration = CommonOAuth2Provider.GOOGLE.getBuilder("google")
        .clientId("google-client-id")
        .clientSecret("google-client-secret")
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .build();

    return new InMemoryClientRegistrationRepository(registration);
}
```

Use `CommonOAuth2Provider` for common providers like Google so current authorization, token, user-info, and JWK set endpoints stay aligned with Spring Security's maintained defaults.

## Outbound OAuth2 client support

Use OAuth2 client support when the application must call downstream protected APIs on behalf of a user or using a client credential.

## Servlet request-scoped user flow

Use this shape when the application is inside an `HttpServletRequest` and needs the authorized client associated with the current user session.

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .oauth2Login(Customizer.withDefaults())
        .oauth2Client(Customizer.withDefaults())
        .build();
}

@Bean
OAuth2AuthorizedClientRepository authorizedClientRepository() {
    return new HttpSessionOAuth2AuthorizedClientRepository();
}

@Bean
OAuth2AuthorizedClientManager authorizedClientManager(
        ClientRegistrationRepository clients,
        OAuth2AuthorizedClientRepository authorizedClientRepository) {
    OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
        .authorizationCode()
        .refreshToken()
        .build();

    DefaultOAuth2AuthorizedClientManager manager =
        new DefaultOAuth2AuthorizedClientManager(clients, authorizedClientRepository);
    manager.setAuthorizedClientProvider(provider);
    return manager;
}
```

Use the manager inside the servlet request flow:

```java
OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId("google")
    .principal(authentication)
    .attributes(attrs -> {
        attrs.put(HttpServletRequest.class.getName(), servletRequest);
        attrs.put(HttpServletResponse.class.getName(), servletResponse);
    })
    .build();

OAuth2AuthorizedClient client = authorizedClientManager.authorize(authorizeRequest);
String accessToken = client.getAccessToken().getTokenValue();
```

## Service or background client-credentials flow

Use this shape when there is no `HttpServletRequest`, such as a scheduled job or message listener.

```java
@Bean
OAuth2AuthorizedClientService authorizedClientService(
        ClientRegistrationRepository clients) {
    return new InMemoryOAuth2AuthorizedClientService(clients);
}

@Bean
OAuth2AuthorizedClientManager authorizedClientManager(
        ClientRegistrationRepository clients,
        OAuth2AuthorizedClientService authorizedClientService) {
    OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
        .clientCredentials()
        .build();

    AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(clients, authorizedClientService);
    manager.setAuthorizedClientProvider(provider);
    return manager;
}
```

Use this manager for service-to-service client-credentials flows:

```java
Authentication systemPrincipal = new AnonymousAuthenticationToken(
    "key",
    "system",
    AuthorityUtils.createAuthorityList("ROLE_SYSTEM")
);

OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId("invoices")
    .principal(systemPrincipal)
    .build();

OAuth2AuthorizedClient client = authorizedClientManager.authorize(authorizeRequest);
String accessToken = client.getAccessToken().getTokenValue();
```

Keep this boundary explicit:

- OAuth2 login authenticates the user into your app.
- OAuth2 client support manages outbound access tokens for downstream calls.
- Request-scoped user flows and service/background flows use different `OAuth2AuthorizedClientManager` implementations.

| Boundary | What it does | When to use it |
| --- | --- | --- |
| `oauth2Login` | Delegates user authentication to an external identity provider | Users sign in with Google, GitHub, or another external IdP |
| `oauth2Client` | Obtains and manages outbound access tokens for downstream APIs | The application calls APIs on behalf of a user or a service principal |
| `oauth2ResourceServer` | Validates incoming bearer tokens | The application receives bearer tokens from callers and does not initiate delegated login |

## Testing shapes

### Mock OAuth2 login

```java
mvc.perform(get("/").with(oauth2Login()))
    .andExpect(status().isOk());
```

### Mock OIDC login

```java
mvc.perform(get("/").with(oidcLogin()))
    .andExpect(status().isOk());
```

Use these helpers only when the test is exercising delegated login behavior. Keep ordinary servlet authorization tests on `@WithMockUser` or `jwt()` in the common path.

## Decision points

| Situation | Use |
| --- | --- |
| User signs in with external provider | OAuth2 login |
| App calls downstream API with managed token | OAuth2 client |
| App validates incoming bearer token only | resource server, not this path |
