# Spring HATEOAS problem details error representations

Open this reference when error responses should use `application/problem+json` instead of ad hoc JSON payloads.

## Problem Details boundary

Use Problem Details when clients need a consistent machine-readable error shape that is distinct from successful hypermedia representations.

Keep successful hypermedia responses and problem-detail error responses as separate media-type paths in controller behavior and tests.

## Error response shape

```json
{
  "type": "https://example.com/problems/order-not-found",
  "title": "Order not found",
  "status": 404,
  "detail": "Order 42 does not exist"
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Successful response with navigable links | HAL or another selected hypermedia media type |
| Standardized machine-readable error response | `application/problem+json` |
| One module mixes hypermedia success payloads and problem-detail errors | keep each media type explicit in tests and controller behavior |
