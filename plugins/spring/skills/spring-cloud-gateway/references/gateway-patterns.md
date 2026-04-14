---
title: Spring Cloud Gateway Patterns Reference
description: >-
  Reference for Spring Cloud Gateway route design, predicate rules, and gateway patterns.
---

Use this reference when the question is how to shape routes and policy at the edge rather than how backend controllers work.

## Core Shape

- predicate decides route match
- filter shapes request or response
- backend URI or load-balanced target handles the business service

## Route Design Checklist

- what request pattern should match
- which path or header transformations are needed
- whether resilience belongs at the gateway or deeper in the system
- whether the backend URI is static or discovery-based

## Predicate Rule

Prefer simple path or host predicates first.
Only stack multiple predicates when the route truly needs combined match semantics.

## Operator Surface Rule

Treat the gateway as an operator-facing component.

- expose the route and gateway actuator surfaces deliberately when runtime route diagnosis matters
- make retry, circuit-breaker, and timeout behavior visible as route policy rather than hidden defaults

## Filter Boundary Rule

Keep gateway filters focused on edge concerns.

- authentication, header shaping, path rewriting, and coarse resilience fit the gateway well
- business orchestration and persistence-aware logic do not

## Predicate Examples

Simple path predicate — match on path only:

```yaml
predicates:
  - Path=/api/**
```

Host predicate — match on a specific host:

```yaml
predicates:
  - Host=api.example.com
```

Path + Header combined — route only when both match:

```yaml
predicates:
  - Path=/api/**
  - Header=X-Request-Type, special
```

## Filter Boundary Examples

Gateway-appropriate filters — authentication, header shaping, path rewriting, coarse resilience:

```yaml
filters:
  - AddRequestHeader=X-Forwarded-By, gateway
  - RemoveRequestHeader=X-Debug
  - RewritePath=/api/(?<segment>.*), /${segment}
```

Not gateway-appropriate — business orchestration, persistence-aware logic, fine-grained retry.

## Route Shape Examples

Load-balanced route via service discovery:

```yaml
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

Static backend route:

```yaml
routes:
  - id: static-backend
    uri: http://legacy-backend:8080
    predicates:
      - Path=/legacy/**
```

Strip, rewrite, retry, and circuit-breaker filter examples belong in the gateway filter guidance selected by the active skill when filter detail is the real blocker.
