# Spring REST Docs links

Open this reference when the contract includes hypermedia links.

## Decision points

| Situation | Use |
| --- | --- |
| Every emitted rel is part of the public contract | `links(...)` |
| Only part of a larger hypermedia response is stable | `relaxedLinks(...)` |
| Atom-format links under non-JSON content type | `links(..., atomLinks())` |
| HAL-format links under non-HAL content type | `links(..., halLinks())` |
| A custom link format | provide your own `LinkExtractor` implementation |

```java
links(linkWithRel("self").description("Canonical self link"),
    linkWithRel("orders").description("Collection link"))
```

```java
relaxedLinks(linkWithRel("self").description("Canonical self link"))
```

Two link formats are understood by default:

- Atom: links in an array named `links`.
  - Default when content type is compatible with `application/json`.
- HAL: links in a map named `_links`.
  - Default when content type is compatible with `application/hal+json`.

When links use a HAL or HAL-FORMS content type, link extraction works automatically.
For non-standard content types, pass an explicit extractor.

Use link snippets when the response is hypermedia-driven.

## Gotchas

- Do not document `self` or `curies` links repeatedly across every endpoint.
  - Document them once in an overview and use `.ignored()` in per-endpoint descriptors.
- The test fails if an undocumented link is found, unless relaxed mode is used.

## Validation rule

Verify the documented rel names match the actual response links.
