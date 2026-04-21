# Spring REST Docs subsection payloads

Open this reference when one payload subtree should be documented without every leaf field.

## Decision points

| Situation | Use |
| --- | --- |
| Every leaf field in the subtree matters | ordinary `fieldWithPath(...)` descriptors |
| One subtree matters but leaf-level documentation would be noisy | `subsectionWithPath(...)` |

```java
responseFields(
    subsectionWithPath("metadata").description("Response metadata")
)
```

Use subsection descriptors when one subtree matters but documenting every leaf would be noisy.

## Validation rule

Verify the chosen subsection boundary still tells the client enough about the contract-relevant subtree.
