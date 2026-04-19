# Spring Cloud Gateway routing

Open this reference when the ordinary ConfigData-discovery-loadbalancer path in [SKILL.md](../SKILL.md) is not enough and the task is specifically about gateway routing or choosing a gateway boundary instead of direct downstream calls.

## Gateway route blocker

Use Gateway when the service itself must own a routing boundary, edge policy, or route-level filter chain.

```java
@Bean
RouteLocator routes(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("catalog", route -> route.path("/catalog/**")
            .filters(filters -> filters.stripPrefix(1).retry(3))
            .uri("lb://catalog-service"))
        .build();
}
```

Keep route ids, path predicates, and retry filters explicit because they become part of the gateway compatibility surface.

## Validation rule

Verify one representative route end to end, including predicate match and rewritten downstream URI.
