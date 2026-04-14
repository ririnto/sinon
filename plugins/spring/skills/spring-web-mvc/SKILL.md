---
name: spring-web-mvc
description: >-
  This skill should be used when the user asks to "build a Spring REST controller", "validate requests", "handle Spring MVC exceptions", "design a Spring Web endpoint", or needs guidance on servlet-based Spring Web MVC patterns.
---

# Spring Web MVC

## Overview

Use this skill to design Spring MVC controllers, request validation, exception mapping, pagination, and response shapes around clear HTTP behavior. The common case is a thin controller that validates at the edge, delegates to a service, and returns a predictable response or RFC 9457-style `ProblemDetail` payload. Keep transport logic in the controller layer and business decisions in services.

## Use This Skill When

- You are building or refactoring servlet-based Spring HTTP endpoints.
- You need a default controller, validation, error-handling, or paginated list-endpoint shape.
- You need to decide whether `ResponseEntity` is justified.
- Do not use this skill when the boundary is reactive rather than servlet-based.

## Common-Case Workflow

1. Start from the HTTP contract: method, path, request body, validation, and response shape.
2. Keep controller responsibilities small: translate HTTP in and out, then delegate to a service.
3. Put validation at the edge and map exceptions consistently with one `ProblemDetail`-first strategy.
4. Choose between `Slice` and `Page` deliberately for list endpoints, and whitelist client-visible sort keys.
5. Use `ResponseEntity` only when the endpoint really controls headers or status behavior.

## Minimal Setup

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

## First Runnable Commands or Code Shape

Start with one thin validated controller:

```java
@RestController
@RequestMapping("/orders")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

---

*Applies when:* you need the default shape for a normal Spring MVC endpoint.

## Ready-to-Adapt Templates

Validated request DTO:

```java
record CreateOrderRequest(@NotBlank String customerId, @Positive int quantity) {
}
```

---

*Applies when:* the endpoint accepts structured input that must be validated at the boundary.

Thin controller:

```java
@RestController
@RequestMapping("/orders")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
```

---

*Applies when:* the endpoint needs explicit status handling or creation semantics.

Consistent error handler:

```java
@RestControllerAdvice
class ApiErrorHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handleNotFound(OrderNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
```

---

*Applies when:* you need uniform error responses instead of controller-local exception code.

Paginated list endpoint:

```java
@GetMapping
Page<OrderSummaryResponse> list(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable) {
    Pageable safePageable = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Direction.DESC, sortProperty(pageable.getSort())));
    return orderService.list(safePageable);
}

private String sortProperty(Sort sort) {
    Set<String> allowedSortProperties = Set.of("createdAt", "status");
    return sort.stream()
            .findFirst()
            .map(Sort.Order::getProperty)
            .filter(allowedSortProperties::contains)
            .orElse("createdAt");
}
```

---

*Applies when:* the endpoint returns a pageable collection and query shape must stay stable over time.

Outbound `RestClient` call:

```java
class OrderClient {
    private final RestClient restClient;

    OrderClient(RestClient restClient) {
        this.restClient = restClient;
    }

    OrderResponse findOne(Long id) {
        return restClient.get()
                .uri("/orders/{id}", id)
                .retrieve()
                .body(OrderResponse.class);
    }
}
```

---

*Applies when:* the shared `RestClient` setup is already enough and one call site just needs a normal request.

## Validate the Result

Validate the common case with these checks:

- controllers stay thin and delegate business work
- request validation happens at the transport edge
- error responses are predictable across endpoints and favor `ProblemDetail`
- paginated endpoints expose deliberate defaults and do not accept arbitrary sort properties blindly
- entities or persistence models are not exposed directly as public API payloads

## Deep References

| If the blocker is... | Read... |
| --- | --- |
| controller structure, DTO boundaries, `ResponseEntity`, pagination, or page serialization choice | `./references/mvc-patterns.md` |
| validation, `ProblemDetail`, and `@RestControllerAdvice` recipes | `./references/mvc-error-validation.md` |
| advanced `RestClient` customization and client-wiring cases | `./references/rest-client-recipes.md` |

## Invariants

- MUST keep controllers thin.
- SHOULD validate input at the edge.
- MUST make error responses predictable and stable.
- SHOULD use `ProblemDetail`-style responses for HTTP error contracts.
- SHOULD separate transport concerns from business logic.
- SHOULD choose `Slice` vs `Page` deliberately rather than defaulting to `Page` everywhere.
- SHOULD decide outbound HTTP customization by case instead of mixing shared and per-client rules together.

## Common Pitfalls

| Anti-pattern | Why it fails | Correct move |
| --- | --- | --- |
| pushing business decisions into controller methods | HTTP transport and application logic get tangled | keep controllers as boundary translators only |
| returning entities directly as public API contracts | persistence concerns leak into external HTTP shape | use request/response DTOs |
| scattering validation and exception payloads across endpoints | client behavior becomes inconsistent | centralize validation and exception mapping |
| exposing raw pageable and sort behavior without defaults or whitelists | clients couple to accidental query behavior and internal property names | set explicit defaults and map sort keys deliberately |
| returning raw `PageImpl` without thinking about public JSON shape | serialized page structure can become a brittle external contract | treat page JSON as a DTO contract and prefer `VIA_DTO` when the API must stay stable |

## Scope Boundaries

- Activate this skill for:
  - servlet-based Spring controller shape
  - validation, exception mapping, and response design
  - blocking outbound HTTP client use in MVC apps
- Do not use this skill as the primary source for:
  - reactive endpoint design
  - persistence-specific rules
  - framework-agnostic REST theory detached from Spring MVC
