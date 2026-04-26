# Spring Security security headers

Open this reference when the default Spring Security response headers are not enough and the application needs custom CSP, HSTS, frame, referrer, or permissions-policy behavior.

Stay with defaults unless a concrete browser or embedding requirement forces a customization.

## Common customizations

```java
.headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'; object-src 'none'")).frameOptions(frame -> frame.sameOrigin()).httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
```

## Decision points

| Situation | Use |
| --- | --- |
| Default hardening is acceptable | keep defaults |
| Browser app needs explicit CSP | set `contentSecurityPolicy(...)` |
| Same-origin framing is required for a console or embedded tool | `frameOptions(frame -> frame.sameOrigin())` |
| HTTPS is mandatory in production | explicit HSTS policy |

## Gotchas

- Do not weaken frame or CSP behavior without a concrete compatibility reason.
- Do not enable HSTS on environments that are not consistently served over HTTPS.
- Do not move header decisions out of security config if the application relies on Spring Security for the final response contract.
