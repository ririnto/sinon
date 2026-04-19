# Spring Security delegated login and OAuth2 client

Open this reference when the ordinary servlet path in `SKILL.md` is not enough and the task depends on delegated login through an external identity provider or outbound OAuth2 client token management.

Keep provider registration, redirect URIs, and granted scopes explicit.

## Delegated login baseline

Use OAuth2 login when the application delegates user authentication to an external identity provider and then creates a local authenticated session.

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
    ClientRegistration registration = ClientRegistration.withRegistrationId("google")
        .clientId("google-client-id")
        .clientSecret("google-client-secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .scope("openid", "profile", "email")
        .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
        .tokenUri("https://www.googleapis.com/oauth2/v4/token")
        .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
        .userNameAttributeName("sub")
        .build();

    return new InMemoryClientRegistrationRepository(registration);
}
```

## Outbound OAuth2 client support

Use OAuth2 client support when the application must call downstream protected APIs on behalf of a user or using a client credential.

```java
@Bean
OAuth2AuthorizedClientService authorizedClientService(
        ClientRegistrationRepository clients) {
    return new InMemoryOAuth2AuthorizedClientService(clients);
}
```

Keep this boundary explicit:

- OAuth2 login authenticates the user into your app.
- OAuth2 client support manages outbound access tokens for downstream calls.

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

## Decision points

| Situation | Use |
| --- | --- |
| User signs in with external provider | OAuth2 login |
| App calls downstream API with managed token | OAuth2 client |
| App validates incoming bearer token only | resource server, not this path |
