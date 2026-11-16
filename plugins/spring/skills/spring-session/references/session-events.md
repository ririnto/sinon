# Session events and listener boundary

- Session lifecycle events and `HttpSessionListener` bridging are additive features, not the ordinary path.
- Open the Redis advanced reference when Spring Security concurrent-session control, audit handling, or administrator-driven invalidation depends on indexed lookup or published session events.

## Clustered Spring Security concurrency

Use `SpringSessionBackedSessionRegistry` when Spring Security's concurrent-session limit must inspect the same indexed repository across application nodes.

For clustered Spring Security concurrency, see [example 3](configuration-examples.md#example-3-clustered-spring-security-concurrency).

This branch requires the indexed Redis repository because `SpringSessionBackedSessionRegistry` depends on principal lookup through `FindByIndexNameSessionRepository`.
