# Spring REST Docs relaxed payloads

Open this reference when relaxed payload documentation is required.

## Decision points

| Situation | Use |
| --- | --- |
| Every field in the payload is part of the contract | strict payload descriptors in `SKILL.md` |
| Extra payload fields exist but are intentionally undocumented | `relaxedResponseFields(...)` |

```java
relaxedResponseFields(
    fieldWithPath("id").description("Order id"),
    fieldWithPath("status").description("Order status")
)
```

Use this only when extra payload fields should not fail the documentation test.

## Validation rule

Verify the relaxed descriptor still covers every field the client truly depends on.
