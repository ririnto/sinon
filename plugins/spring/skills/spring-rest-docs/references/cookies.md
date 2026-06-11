# Spring REST Docs cookies

Open this reference when the contract depends on cookies.

## Decision points

| Situation | Use |
| --- | --- |
| A cookie is part of the public API contract | document it |
| The cookie is framework-internal or deployment-specific | keep it out of published docs |
| Only a subset of cookies matters in one scenario | `relaxedRequestCookies` / `relaxedResponseCookies` |
| A cookie must exist but should not appear in published docs | `.ignored()` |

```java
requestCookies(cookieWithName("SESSION").description("Session identifier"))
```

```java
responseCookies(cookieWithName("JSESSIONID").description("Updated session token"),
    cookieWithName("logged_in").description("User is logged in"))
```

Use cookie documentation only when cookies are part of the published contract. The test fails if a documented cookie is not found in the request or response, unless the cookie is marked as optional.

## Gotchas

- Do not document framework-internal cookies as public API by accident.
