# Spring GraphQL advanced DataLoader patterns

Open this reference when the blocker is DataLoader registration, `DataLoaderOptions`, shared batching lifecycle, or batch-loader context beyond the ordinary `@BatchMapping` path in [SKILL.md](../SKILL.md).

## DataLoader registration blocker

Use explicit registration when batching must be shared across several resolvers or keyed differently from one schema field.

```java
BatchLoaderRegistry registry = new DefaultBatchLoaderRegistry();
registry.forName("reviews")
    .registerMappedBatchLoader((ids, env) -> Mono.just(reviewService.findByBookIds(ids)));
```

Keep loader names and key types explicit so resolver wiring stays predictable.

## DataLoader options blocker

Use `DataLoaderOptions` when batching, caching, or dispatch behavior must differ from the defaults.

```java
DataLoaderOptions options = DataLoaderOptions.newOptions().setCachingEnabled(false);
```

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
