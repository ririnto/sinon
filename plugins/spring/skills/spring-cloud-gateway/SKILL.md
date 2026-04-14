---
name: spring-cloud-gateway
description: >-
  This skill should be used when the user asks to "configure Spring Cloud Gateway", "define routes and filters", "use route predicates", "add retry or circuit breaker at the gateway", or needs guidance on Spring Cloud Gateway patterns.
---

# Spring Cloud Gateway

## Overview

Use this skill to design Spring Cloud Gateway routes, predicates, filters, backend integration, and policy structure. The common case is one clear route match, one small filter chain, and one explicit backend target. Focus on edge behavior and route clarity before adding resilience or header-manipulation complexity.

## Use This Skill When

- You are configuring gateway routes, predicates, or filters.
- You need to rewrite paths or apply gateway resilience policy.
- You need a default Spring Cloud Gateway route shape you can paste into a project.
- Do not use this skill when the main problem is internal controller design rather than gateway edge behavior.

## Common-Case Workflow

1. Start from the inbound path, host, or header rule that decides the route.
2. Add only the filters needed to shape the request or response.
3. Keep resilience filters explicit and tied to a real backend behavior need.
4. Make backend URIs and service-discovery assumptions obvious.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one explicit route and one path-strip filter:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: api-route
          uri: http://backend:8080
          predicates:
            - Path=/api/**
          filters:
            - StripPrefix=1
```

---

*Applies when:* the backend should not see the public gateway prefix.

## Ready-to-Adapt Templates

Path-based route — one backend behind one public prefix:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: api-route
          uri: http://backend:8080
          predicates:
            - Path=/api/**
          filters:
            - StripPrefix=1
```

Rewrite path route — when the backend path contract differs non-trivially:

```yaml
filters:
  - RewritePath=/api/(?<segment>.*), /${segment}
```

Circuit-breaker route — resilience policy at the gateway edge with a defined fallback:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: orders
          uri: lb://orders-service
          predicates:
            - Path=/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: ordersCircuitBreaker
                fallbackUri: forward:/fallback/orders
```

## Validate the Result

Validate the common case with these checks:

- the route-matching rule is obvious from path, host, or header predicates
- the filter chain is as small as the policy allows
- path rewrites and strip rules align with backend expectations
- retry and circuit-breaker behavior are only used when backend failure semantics justify them

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| route shape, predicate choice, or edge-policy heuristics | `./references/gateway-patterns.md` |
| strip, rewrite, retry, or circuit-breaker filter recipes | `./references/gateway-filter-recipes.md` |

## Invariants

- MUST keep route matching rules obvious.
- SHOULD use the smallest filter chain that satisfies the policy.
- MUST keep resilience filters tied to real backend needs.
- SHOULD make forwarding assumptions explicit.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| piling too many unrelated filters onto one route | route intent becomes hard to reason about | keep route-local policy minimal and explicit |
| rewriting paths without documenting the backend expectation | gateway and backend contracts drift silently | state the backend path assumption in the route shape |
| adding retry or circuit-breaker behavior before understanding backend failure modes | resilience policy becomes decorative rather than correct | define backend failure behavior first |

## Scope Boundaries

- Activate this skill for:
  - gateway route design
  - edge filter policy
  - backend forwarding through Spring Cloud Gateway
- Do not use this skill as the primary source for:
  - internal controller design
  - generic service resilience design outside the gateway boundary
