# Spring REST Docs links

Open this reference when the contract includes hypermedia links.

## Decision points

| Situation | Use |
| --- | --- |
| Every emitted rel is part of the public contract | `links(...)` |
| Only part of a larger hypermedia response is stable | `relaxedLinks(...)` |

```java
links(
    linkWithRel("self").description("Canonical self link"),
    linkWithRel("orders").description("Collection link")
)
```

```java
relaxedLinks(
    linkWithRel("self").description("Canonical self link")
)
```

Use link snippets when the response is hypermedia-driven.

## Validation rule

Verify the documented rel names match the actual response links.
