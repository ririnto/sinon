# Spring REST Docs path parameters

Open this reference when the contract depends on URI template variables.

## Decision points

| Situation | Use |
| --- | --- |
| The endpoint path contains public URI variables | `pathParameters(...)` |
| The segment is an internal routing detail only | keep it out of the public descriptor set |

```java
pathParameters(
    parameterWithName("orderId").description("Order identifier")
)
```

Verify the documented path variable names match the URI template exactly.
