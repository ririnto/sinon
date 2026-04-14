---
title: Spring Security Patterns Reference
description: >-
  Additive patterns for Spring Security: error contract design and cross-links to config recipes.
---

Use this reference for error contract depth and cross-links when the access model is already defined.

## Error Contract Rule

Security failures are part of the public API contract.

- authentication failures should have one predictable response shape
- authorization failures should be distinguishable from authentication failures
- Actuator or operator endpoints should not silently reuse the same access rules as public traffic unless that is intended

### Authentication Failure: `AuthenticationException` → ProblemDetail

```java
@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Authentication is required for this resource.");
        problem.setTitle("Authentication Required");
        problem.setProperty("error", "authentication");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
```

### Authorization Failure: `AccessDeniedException` → ProblemDetail

```java
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDenied) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "The current principal is not allowed to access this resource.");
        problem.setTitle("Access Denied");
        problem.setProperty("error", "authorization");
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
```

The two error shapes must differ in HTTP status (`401` vs `403`) and in the `error` property so API clients can act on them without parsing the message text.

Multiple filter-chain and CORS/CSRF configuration recipes should stay in the active security configuration guidance instead of sending readers to another reference.
