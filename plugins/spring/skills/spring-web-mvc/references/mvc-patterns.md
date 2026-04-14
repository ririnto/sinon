---
title: Spring Web MVC Patterns Reference
description: >-
  Deeper decision rules for Slice vs Page, page serialization contracts, and JSON naming strategies.
---

Use this reference when the controller shape is clear and deeper pagination or serialization decisions are needed.

## `Slice` vs `Page` Rule

Prefer `Slice` when the client only needs `hasNext` semantics and total-count accuracy would add avoidable count-query cost.

Prefer `Page` when the client truly needs:

- total element count
- total page count
- explicit page number metadata as part of the public contract

Do not default to `Page` when infinite scroll or next-page-only behavior is enough.

## Page Serialization Rule

Treat serialized page payloads as an external API contract, not as an accidental side effect of returning `PageImpl` directly.

- If the public contract must stay stable across framework changes, prefer a DTO-wrapped page response or Spring Data web support configured with `VIA_DTO`.
- Do not assume the raw `PageImpl` JSON shape is the best long-term public format just because it is convenient in a first controller draft.

Stable page DTO shape:

```java
record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
```

## Serialization Boundary Rule

Use transport-focused Jackson annotations on response DTOs only when the HTTP contract truly needs them.

```java
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
record UserSummaryResponse(
        Long id,
        String displayName,
        Instant createdAt,
        String optionalNote
) {
}
```

Use this when the public API naming convention or omission policy differs from internal Java naming.

Canonical controller and pagination templates belong in the parent skill entrypoint.

Validation, `ProblemDetail`, and `@RestControllerAdvice` detail should stay in the active MVC error-handling guidance rather than behind another local link.
