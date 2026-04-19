# Spring Security session management and logout

Open this reference when the application depends on concurrent-session control, stateful session persistence rules beyond the ordinary path, or custom logout success handling.

Use stateless session policy for bearer-token APIs and stateful session policy only for browser-login flows that actually need a server-side session.

## Concurrent-session control

```java
.sessionManagement(session -> session
    .maximumSessions(1)
    .maxSessionsPreventsLogin(true))
```

Use this when one active login per user is a business or compliance requirement.

## Custom logout success handling

```java
@Bean
LogoutSuccessHandler logoutSuccessHandler() {
    return (request, response, authentication) -> {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    };
}
```

```java
.logout(logout -> logout
    .logoutUrl("/api/logout")
    .logoutSuccessHandler(logoutSuccessHandler())
    .addLogoutHandler(new HeaderWriterLogoutHandler(
        new ClearSiteDataHeaderWriter(ClearSiteDataHeaderWriter.Directive.ALL))))
```

## Decision points

| Situation | Use |
| --- | --- |
| Pure bearer-token API | `SessionCreationPolicy.STATELESS` in the ordinary path |
| Browser login with server-side session | stateful session policy |
| One active session per user | concurrent-session control |
| Logout must return `204 No Content` or clear browser state | custom logout success handler and `Clear-Site-Data` |

## Gotchas

- Do not add servlet-session persistence to a JWT API unless the application really mixes browser login and token validation.
- Do not assume `/logout` matters for stateless bearer-token APIs.
- Do not use concurrent-session control without confirming how clustered session storage works in production.
