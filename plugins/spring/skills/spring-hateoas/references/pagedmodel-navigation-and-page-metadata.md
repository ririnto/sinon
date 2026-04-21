# Spring HATEOAS paged model navigation and page metadata

Open this reference when clients depend on `prev`, `next`, `first`, or `last` navigation links, or when page metadata semantics must be verified precisely.

## Paged model assembly

```java
PagedModel<OrderModel> model = pagedResourcesAssembler.toModel(page, assembler);
```

Use the paged assembler when the API should expose both item representations and top-level page navigation.

## Controller integration shape

```java
@GetMapping("/orders")
PagedModel<OrderModel> all(Pageable pageable) {
    Page<Order> page = service.findAll(pageable);
    return pagedResourcesAssembler.toModel(page, assembler);
}
```

Inject `PagedResourcesAssembler<Order>` into the controller path that already returns a `Page<Order>`. Keep item link generation inside the ordinary assembler so paged navigation and item links stay aligned.

## Paged response shape

```json
{
  "_links": {
    "self": {
      "href": "/orders?page=1&size=20"
    },
    "prev": {
      "href": "/orders?page=0&size=20"
    },
    "next": {
      "href": "/orders?page=2&size=20"
    }
  },
  "page": {
    "size": 20,
    "totalElements": 45,
    "totalPages": 3,
    "number": 1
  }
}
```

## Guardrails

- Verify navigation links only appear when the page state actually supports them.
- Treat page metadata and top-level navigation links as part of the public response contract.
- Keep page numbering assumptions consistent between controller inputs, generated links, and tests.

## Validation rule

Verify the controller, `Pageable` inputs, generated navigation links, and `page` metadata all use the same numbering and sizing assumptions in both runtime code and tests.
