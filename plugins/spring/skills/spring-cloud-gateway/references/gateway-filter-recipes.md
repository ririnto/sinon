---
title: Spring Cloud Gateway Filter Recipes
description: >-
  Reference for Spring Cloud Gateway filter configuration and route filter recipes.
---

Use this reference when the route match is already clear and the remaining work is filter or resilience composition.

## Retry Recipe

Apply retry when the backend call is safe to repeat and the failure mode is genuinely transient.

```yaml
filters:
  - name: Retry
    args:
      retries: 3
      series: SERVER_ERROR
      exceptions: java.io.IOException, java.net.ConnectException
      backoff:
        firstInterval: 100
        interval: 200
        maxInterval: 1000
        multiplier: 2.0
```

Backoff is exponential by default when `backoff` is specified. `firstInterval` is the initial wait; `interval` is the base for subsequent waits capped at `maxInterval`. `multiplier` scales the backoff factor.

## Filter Ordering Rule

Keep route-local filters near the route definition.
Use global filters only for truly shared cross-cutting policy.

Spring Cloud Gateway filters execute in the order defined under `filters:` for a single route.
Global filters apply to all routes after route-local filters.

## Circuit Breaker Recipe

```yaml
filters:
  - name: CircuitBreaker
    args:
      name: ordersCircuitBreaker
      fallbackUri: forward:/fallback/orders
```

The fallback target is a controller endpoint. CircuitBreaker uses Resilience4j under the hood; configure the circuit breaker instance via the Spring Cloud Gateway Resilience4j properties if non-default timeouts or thresholds are needed.

## StripPrefix and RewritePath

StripPrefix removes path segments from the front:

```yaml
filters:
  - StripPrefix=1
```

RewritePath performs arbitrary regex-based substitution:

```yaml
filters:
  - RewritePath=/api/(?<segment>.*), /${segment}
```

StripPrefix is simpler and preferred when removing a fixed number of leading segments is sufficient. RewritePath handles non-trivial path contracts.

## Request Header Filters

Add or remove headers at the gateway edge:

```yaml
filters:
  - AddRequestHeader=X-Forwarded-By, gateway
  - RemoveRequestHeader=X-Debug
```

## Response Header Filters

```yaml
filters:
  - AddResponseHeader=X-Gateway-Processed, gateway
```

## Rate Limiting (RequestSize)

Route-level size limit as a coarse filter:

```yaml
filters:
  - name: RequestSize
    args:
      name: maxPayload
      size: 5MB
```

Place this filter early in the filter chain to catch oversized payloads before they reach the backend.
