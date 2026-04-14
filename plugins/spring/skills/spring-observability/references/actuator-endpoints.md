---
title: Spring Actuator Endpoint Reference
description: >-
  Reference for Spring Boot Actuator security, management port, health groups, and production pitfalls.
---

Use this reference when the main question is which Actuator endpoints to expose, how to secure them, and how to shape readiness/liveness surfaces for operators.

## Dedicated Management Port

Use a dedicated management port when operator traffic should stay separate from user traffic. A split port allows network policy to distinguish management calls from application calls without relying on path-based filtering alone.

```yaml
management:
  server:
    port: 8081
```

When to use a dedicated port:

- the operator tooling runs on a different network segment than end-user traffic
- security policy requires explicit firewall rules for management access
- you want to apply different rate-limiting or authentication rules to management calls

## Security Matcher for Actuator Endpoints

Protect operator endpoints with an explicit Spring Security matcher so that authorization rules are scoped to management endpoints and do not accidentally affect user-facing routes.

```java
@Bean
SecurityFilterChain actuatorSecurity(HttpSecurity http) throws Exception {
    http.securityMatcher(EndpointRequest.toAnyEndpoint())
            .authorizeHttpRequests(requests -> requests.anyRequest().hasRole("OPS"));
    return http.build();
}
```

Note that `EndpointRequest.toAnyEndpoint()` covers all Actuator endpoints; use `EndpointRequest.to("health", "info")` to scope to only the endpoints that should be publicly accessible.

## Show-Details Policy

Prefer `when-authorized` over `always` or `never` for health details. The `when-authorized` setting keeps sensitive component-level information behind an authentication check while keeping the basic status available to unauthenticated orchestrators such as Kubernetes liveness probes.

```yaml
management:
  endpoint:
    health:
      show-details: when-authorized
```

## Health Group Alignment with Kubernetes Probes

When probes are enabled, Spring Boot maps `liveness` and `readiness` groups directly to Kubernetes probe endpoints:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: db,redis,diskSpace
        liveness:
          include: ping
```

Align group membership with these semantics:

- **readiness**: reflects dependency availability before the app receives traffic. Include only components whose unavailability should block traffic routing.
- **liveness**: reflects whether the app process is healthy enough to be restarted. Keep this narrow; a broad liveness group causes unnecessary restarts when non-critical dependencies are slow.

## Production Pitfalls

- exposing `env`, `beans`, or `configprops` casually in production; these leak internal application state and should never be publicly accessible
- keeping the management port on the same port as user traffic when network policy expects separation
- treating a single `UP` health result as sufficient readiness signal without checking real dependencies
- using `show-details: always` when unauthenticated callers can reach the endpoint
