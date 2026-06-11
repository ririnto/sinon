# Spring REST Docs HTTP headers

Open this reference when the contract depends on request or response headers beyond the simple inline shape in `SKILL.md`.

## Decision points

| Situation | Use |
| --- | --- |
| Every documented header must be present | `requestHeaders(...)`, `responseHeaders(...)` |
| A header may or may not appear | `.optional()` on the descriptor |
| A header must exist but should not appear in docs | `.ignored()` |
| Only a subset of headers matters in one scenario | `relaxedRequestHeaders(...)`, `relaxedResponseHeaders(...)` |

```java
requestHeaders(headerWithName("Authorization").description("Basic auth credentials"))
```

```java
responseHeaders(headerWithName("X-RateLimit-Limit").description("Total requests permitted per period"),
    headerWithName("X-RateLimit-Remaining").description("Remaining requests in current period"),
    headerWithName("X-RateLimit-Reset").description("Time at which the rate limit resets"))
```

The test fails if a documented header is not found in the request or response, unless the header is marked as optional.

## Gotchas

- Do not document internal or framework-generated headers as public API.
- Document only headers that the client must set or inspect.

## Validation rule

Verify every documented header name appears in the actual request or response.
