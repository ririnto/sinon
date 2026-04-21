# Spring REST Docs query parameters

Open this reference when the contract depends on query parameters.

## Decision points

| Situation | Use |
| --- | --- |
| The client must supply or understand query string keys | `queryParameters(...)` |
| The parameter is internal or framework-generated only | do not document it as public API |

```java
queryParameters(
    parameterWithName("status").description("Order status filter")
)
```

Verify every documented query parameter appears in the actual request template or test call.
