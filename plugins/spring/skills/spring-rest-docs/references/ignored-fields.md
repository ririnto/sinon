# Spring REST Docs ignored fields

Open this reference when a field must exist in the payload but should stay out of published docs.

```java
responseFields(
    fieldWithPath("_internal").ignored()
)
```

Use ignored fields when a field must exist in the payload but should not appear in published docs.

## Gotchas

- Do not ignore a field that clients are actually supposed to rely on.
