# Spring REST Docs cookies

Open this reference when the contract depends on cookies.

```java
requestCookies(
    cookieWithName("SESSION").description("Session identifier")
)
```

Use cookie documentation only when cookies are part of the published contract.

## Gotchas

- Do not document framework-internal cookies as public API by accident.
