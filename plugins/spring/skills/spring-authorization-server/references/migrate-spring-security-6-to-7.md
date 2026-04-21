# Migrating Spring Security 6 to 7

Open this reference when the task is specifically migrating the authorization server configuration from Spring Security 6 to Spring Security 7.

## Spring Security migration blocker

**Problem:** filter-chain APIs or session behavior changed and the authorization server no longer behaves the same across login and authorization redirects.

**Solution:** update the filter-chain configuration and verify session behavior explicitly on the authorization path.

```java
@Bean
@Order(Ordered.HIGHEST_PRECEDENCE)
SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
    http.oauth2AuthorizationServer(authorizationServer -> {
        http.securityMatcher(authorizationServer.getEndpointsMatcher());
        authorizationServer.oidc(Customizer.withDefaults());
    });
    return http.exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), new MediaTypeRequestMatcher(MediaType.TEXT_HTML))).build();
}
```

## Session behavior branch

```java
http.sessionManagement(session -> session.sessionFixation(fixation -> fixation.none()));
```

Use custom session-fixation handling only when the authorization flow explicitly depends on preserving the existing session behavior.

## Decision points

| Situation | First check |
| --- | --- |
| Authorization redirect no longer reaches login correctly | verify the authorization-server chain entry point wiring |
| Session state disappears during login or consent | verify Spring Security 7 session-fixation behavior |
| Customizer-based config stops compiling | verify the Spring Security 7 API shape used by the filter chain |

## Pitfalls

- Do not treat Spring Security framework migration as the same job as a general authorization-server feature upgrade.
- Do not disable session protections without confirming the exact authorization-flow requirement.
- Re-run authorization, token, and consent-path tests after every filter-chain migration change.
