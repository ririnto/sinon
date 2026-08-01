# Spring Cloud Gateway routing

Open this reference when the ordinary ConfigData-discovery-loadbalancer path is not enough and the task is specifically about gateway routing or choosing a gateway boundary instead of direct downstream calls.

## Artifact rename (2025.0.x+)

New starter names were introduced in Spring Cloud Gateway 4.3.x.
Use the current names in new configuration.

| Previous | New (2025.0.x+) |
| --- | --- |
| `spring-cloud-starter-gateway` | `spring-cloud-starter-gateway-server-webflux` |
| `spring-cloud-starter-gateway-mvc` | `spring-cloud-starter-gateway-server-webmvc` |

Use the new artifact names for all new work.
For existing estates on the old names, add `spring-boot-properties-migrator` to identify property prefix changes during migration.

## Property prefix migration (2025.0.x+)

| Old prefix | New prefix |
| --- | --- |
| `spring.cloud.gateway.*` | `spring.cloud.gateway.server.webflux.*` |
| `spring.cloud.gateway.mvc.*` | `spring.cloud.gateway.server.webmvc.*` |
| `spring.cloud.gateway.proxy.*` | `spring.cloud.gateway.proxy-exchange.webflux.*` |

Use the new prefixes for all new configuration.

## Trusted proxies (2025.0.x+)

`X-Forwarded-*` and `Forwarded` header handling is disabled by default.
Set trusted proxies explicitly:

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          trusted-proxies: "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}"
```

> [!CAUTION]
>
> CVE-2026-47825 (Gateway 5.0.2): WebMVC and WebFlux Gateway Server could forward `X-Forwarded-For` and `Forwarded` headers from untrusted proxies under certain configurations.
> `NettyServerCustomizer` is now disabled by default.
> Re-enable only when the downstream proxy chain is explicitly trusted:
>
> ```yaml
> spring:
>   cloud:
>     gateway:
>       server:
>         webflux:
>           httpserver:
>             customizer-enabled: true
> ```

## StripContextPath filter (5.0.2+)

In the WebMVC variant, add `StripContextPath` before path-manipulating filters when `server.servlet.context-path` is configured:

```properties
spring.cloud.gateway.server.webmvc.routes[0].predicates[0]=Path=/microservice-one/**
spring.cloud.gateway.server.webmvc.routes[0].filters[0]=StripContextPath
spring.cloud.gateway.server.webmvc.routes[0].filters[1]=StripPrefix=1
```

## CodecCustomizer for body filters (5.0.2+)

Body filter codec encoding is customizable through `CodecCustomizer` beans instead of requiring direct `HttpClient` customization.

## Gateway route blocker

Use Gateway when the service itself must own a routing boundary, edge policy, or route-level filter chain.

```java
@Bean
RouteLocator routes(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("catalog", route -> route.path("/catalog/**").filters(filters -> filters.stripPrefix(1).retry(3)).uri("lb://catalog-service"))
        .build();
}
```

Keep route ids, path predicates, and retry filters explicit because they become part of the gateway compatibility surface.

## Validation rule

Verify one representative route end to end, including predicate match and rewritten downstream URI.
