# Spring HATEOAS representation processors

Open this reference when one cross-cutting rule must enrich many representations with the same extra links or metadata after the ordinary assembler step, especially for collection or paged models that need post-assembly enrichment.

## Processor boundary

Use `RepresentationModelProcessor` when the enrichment rule is shared across many representation types or when enrichment must happen after the ordinary assembler step has already produced the model.

```java
@Component
class OrdersCollectionProcessor implements RepresentationModelProcessor<CollectionModel<OrderModel>> {
    @Override
    public CollectionModel<OrderModel> process(CollectionModel<OrderModel> model) {
        return model.add(Link.of("/docs/orders", "profile"));
    }
}
```

Prefer assemblers for resource-specific links and processors for cross-cutting enrichment that should apply after item or collection assembly is complete.

## Typical blocker shapes

- add the same documentation or profile link to many collection responses
- enrich every paged model with one shared top-level link
- apply one cross-cutting rule without duplicating it across many assemblers

## Decision points

| Situation | Use |
| --- | --- |
| One representation has its own local link rules | assembler |
| Many representations need the same extra link or metadata after assembly | representation processor |
| Links should derive from an aggregate type rather than controller methods | entity links reference |
