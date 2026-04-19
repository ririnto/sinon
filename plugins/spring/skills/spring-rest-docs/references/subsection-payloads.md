# Spring REST Docs subsection payloads

Open this reference when one payload subtree should be documented without every leaf field.

```java
responseFields(
    subsectionWithPath("metadata").description("Response metadata")
)
```

Use subsection descriptors when one subtree matters but documenting every leaf would be noisy.
