# Spring HATEOAS forwarded headers and proxy configuration

Open this reference when generated links are wrong behind a reverse proxy, gateway, ingress, or base-path rewrite.

## Forwarded-header filter shape

```java
@Bean
ForwardedHeaderFilter forwardedHeaderFilter() {
    return new ForwardedHeaderFilter();
}
```

Use forwarded-header handling when the externally visible scheme, host, port, or base path differs from the local container request seen by the application.

## Symptoms

- generated links point to the internal host or port
- generated links use `http` when the public endpoint is `https`
- generated links miss the external base path added by a gateway

## Guardrails

- Verify `_links.self.href` from a deployed environment, not only from local tests.
- Keep proxy and application forwarding rules aligned so generated links stay canonical.
- Re-check affordance and paged links after any gateway or ingress rewrite change.
