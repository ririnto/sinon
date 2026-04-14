---
title: Spring WebFlux Patterns Reference
description: >-
  Reference for Spring WebFlux endpoint shape, route style, and reactive contract decisions.
---

Use this reference when the question is about how a reactive HTTP application should be shaped, not just whether `Mono` or `Flux` exists.

## Core Decisions

- annotated controller vs functional routing
- `Mono` vs `Flux`
- reactive handler vs ordinary servlet endpoint

## Annotated Controller Recipe

```java
@RestController
@RequestMapping("/products")
class ProductController {

    @GetMapping("/{id}")
    Mono<ProductResponse> findOne(@PathVariable Long id) {
        return service.findOne(id);
    }
}
```

Use annotated style when the rest of the codebase already uses controller annotations.

## Functional Route Recipe

```java
@Bean
RouterFunction<ServerResponse> productRoutes(ProductHandler handler) {
    return RouterFunctions.route()
            .GET("/products/{id}", handler::findOne)
            .build();
}
```

Use functional routes when the team prefers explicit route composition or gateway-like handler code.

## `Mono` vs `Flux` Rule

- `Mono` for zero-or-one result semantics
- `Flux` for multiple values or streaming semantics

## Validation and Error Contract Rule

Keep WebFlux HTTP contracts as deliberate as MVC contracts.

- use one predictable error shape instead of ad hoc responses from each handler
- treat validation and decoding failures as part of the reactive HTTP contract, not as incidental framework noise

## Blocking Boundary Rule

Do not hide blocking work inside a reactive handler path.

- if the persistence or downstream client boundary is blocking, it should not be smuggled into WebFlux as if the stack remained fully reactive
- scheduler hopping is not a substitute for a clear end-to-end reactive design decision

## Streaming Rule

Use streaming responses only when the client contract really benefits from them.

- server-sent events or long-lived streams should have explicit cancellation and backpressure expectations
- do not return `Flux` just because several items exist if a collected `Mono<List<T>>` is the actual contract

`WebClient`, codec, and client-side operational details belong in the active reactive-client guidance selected for the task rather than through another reference link.
