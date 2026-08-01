# Spring GraphQL advanced DataLoader patterns

Open this reference when the blocker is DataLoader registration, `DataLoaderOptions`, shared batching lifecycle, or batch-loader context beyond the ordinary `@BatchMapping` path.

## DataLoader registration blocker

Use explicit registration when batching must be shared across several resolvers or keyed differently from one schema field.
Inject Spring GraphQL's framework-managed `BatchLoaderRegistry` instead of creating a disconnected registry.

```java
@Configuration
class GraphQlDataLoaderConfig {
    GraphQlDataLoaderConfig(BatchLoaderRegistry registry) {
        registry.forName("reviews")
            .withOptions(options -> options.setCachingEnabled(false))
            .registerMappedBatchLoader((ids, env) -> Mono.just(reviewService.findByBookIds(ids)));
    }
}
```

Keep loader names and key types explicit so resolver wiring stays predictable.

## DataLoader options blocker

Use `DataLoaderOptions` when batching, caching, or dispatch behavior must differ from the defaults.

Apply options to the registration that consumes them.

Change options only when the loader lifecycle or cache behavior is part of the real problem.

## Batch context blocker

Use batch-loader context when request-scoped metadata must reach the batch load itself rather than only the resolver method.

## Decision points

| Situation | First check |
| --- | --- |
| One nested field is the only batching boundary | stay on `@BatchMapping` |
| Several resolvers should reuse the same batch loader | register a named DataLoader |
| Loader caching or dispatch is wrong | inspect `DataLoaderOptions` |
| Batch logic needs request metadata | pass and read batch-loader context explicitly |

## Validation rule

Verify the chosen loader removes the N+1 access pattern and that key types, loader names, and cache behavior all match the resolver contract.
