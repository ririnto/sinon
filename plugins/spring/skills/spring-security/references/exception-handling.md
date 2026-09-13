# Spring Security exception handling

Open this reference when the application needs custom 401 or 403 responses, stable JSON error bodies, or explicit handling of authentication versus authorization failures.

Keep `AuthenticationEntryPoint` and `AccessDeniedHandler` separate because unauthenticated and insufficiently authorized requests are different failure modes.

## 401 versus 403 boundary

- Use `AuthenticationEntryPoint` for unauthenticated requests that should return `401 Unauthorized`.
- Use `AccessDeniedHandler` for authenticated requests that should return `403 Forbidden`.

## Basic JSON handlers

```java
@Bean
AuthenticationEntryPoint authenticationEntryPoint() {
    return (request, response, exception) -> {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"unauthorized\"}");
    };
}

@Bean
AccessDeniedHandler accessDeniedHandler() {
    return (request, response, exception) -> {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"forbidden\"}");
    };
}
```

Wire both handlers into the servlet chain.

```java
.exceptionHandling(exceptions -> exceptions
    .authenticationEntryPoint(authenticationEntryPoint())
    .accessDeniedHandler(accessDeniedHandler()))
```

## Decision points

| Situation | Use |
| --- | --- |
| Anonymous request hits a protected API | `AuthenticationEntryPoint` |
| Authenticated user lacks authority | `AccessDeniedHandler` |
| API clients need a stable JSON contract | explicit JSON handlers |
| Browser login flow should redirect to login | use login defaults instead of JSON entry-point behavior |

## Gotchas

- Do not return 403 for an anonymous request unless the product contract explicitly requires it.
- Do not share one handler for both authentication and authorization failures if the client depends on different status codes.
- Do not force JSON entry-point behavior onto browser login flows that should redirect.
