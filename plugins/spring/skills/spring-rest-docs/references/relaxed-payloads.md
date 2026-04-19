# Spring REST Docs relaxed payloads

Open this reference when relaxed payload documentation is required.

```java
relaxedResponseFields(
    fieldWithPath("id").description("Order id"),
    fieldWithPath("status").description("Order status")
)
```

Use this only when extra payload fields should not fail the documentation test.
