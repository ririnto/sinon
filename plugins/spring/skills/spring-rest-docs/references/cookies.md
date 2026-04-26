# Spring REST Docs cookies

Open this reference when the contract depends on cookies.

## Decision points

| Situation | Use |
| --- | --- |
| A cookie is part of the public API contract | document it |
| The cookie is framework-internal or deployment-specific | keep it out of published docs |

```java
requestCookies(cookieWithName("SESSION").description("Session identifier"))
```

Use cookie documentation only when cookies are part of the published contract.

## Gotchas

- Do not document framework-internal cookies as public API by accident.
