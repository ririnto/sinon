# OIDC provider endpoints

Open this reference when the provider must expose OIDC identity endpoints, UserInfo, logout handling, or ID-token behavior beyond the OAuth2-only baseline in [SKILL.md](../SKILL.md).

## OIDC enablement rule

Enable OIDC in the authorization server filter chain when clients need ID tokens, UserInfo, or OpenID provider metadata.

```java
http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
    .oidc(Customizer.withDefaults());
```

Without this, the provider acts as a pure OAuth2 authorization server with no identity surface.

## Provider metadata endpoint

The `.oidc()` configuration exposes the OpenID Provider Configuration endpoint at `/.well-known/openid-configuration`.

Verify it returns the expected shape:

```json
{
  "issuer": "http://localhost:8080",
  "authorization_endpoint": "http://localhost:8080/oauth2/authorize",
  "token_endpoint": "http://localhost:8080/oauth2/token",
  "userinfo_endpoint": "http://localhost:8080/userinfo",
  "jwks_uri": "http://localhost:8080/oauth2/jwks",
  "end_session_endpoint": "http://localhost:8080/connect/logout"
}
```

## UserInfo endpoint

The UserInfo endpoint returns identity claims for the authenticated user. It requires a valid access token with the `openid` scope.

### Preferred path: customize the ID token claims

The official preferred path is to add standard claims to the ID token and let the default UserInfo response derive from those claims.

```java
@Bean
OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(OidcUserInfoService userInfoService) {
    return context -> {
        if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            OidcUserInfo userInfo = userInfoService.loadUser(context.getPrincipal().getName());
            context.getClaims().claims(claims -> claims.putAll(userInfo.getClaims()));
        }
    };
}
```

### Full UserInfo mapper customization

When the UserInfo response itself must be shaped directly, customize the official UserInfo mapper on the OIDC endpoint.

```java
http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
    .oidc(oidc -> oidc
        .userInfoEndpoint(userInfo -> userInfo
            .userInfoMapper(context -> new OidcUserInfo(Map.of("sub", context.getAuthentication().getName())))
        )
    );
```

## Logout endpoint

The built-in logout endpoint is at `/connect/logout`. It initiates single logout by presenting a URL the client can redirect the user to.

### Logout request validation

```java
@Configuration
class LogoutConfig {
    @Bean
    SecurityFilterChain logoutChain(HttpSecurity http) throws Exception {
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
            .oidc(oidc -> oidc.logoutEndpoint(logout -> {}));
        return http.build();
    }
}
```

### Post-logout redirect

```java
.redirectUri("http://127.0.0.1:8080/logged-out")
.postLogoutRedirectUri("http://127.0.0.1:8080/logged-out")
```

## Decision table

| Situation | Use |
| --- | --- |
| Clients need ID tokens | Enable `.oidc()` and add `openid` scope to registered clients |
| Customize UserInfo claims | Prefer ID-token customization or configure the official `userInfoMapper` seam |
| Provider-initiated logout | Configure logout endpoint and register post-logout URIs per client |

## Official documentation

- [Protocol endpoints](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/protocol-endpoints.html)
- [Configuration model](https://docs.spring.io/spring-security/reference/servlet/oauth2/authorization-server/configuration-model.html)
