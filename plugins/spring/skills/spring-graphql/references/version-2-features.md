# Spring GraphQL 2.0 features

- JSpecify nullability annotations on the entire public API.
  - IDEs can warn about `NullPointerException` risks at compile time.
- Schema Mapping Inspection checks nullability declared in the schema (`Book` vs `Book!`) against controller method signatures and reports mismatches.
- Flexible input binding via `GraphQlArgumentBinder.Options` with a `nameResolver` function for adapting schema argument names to Java property naming conventions.
  - Set the options on `AnnotatedControllerConfigurer#setBinderOptions`.
- `ArgumentValue<T>` wrapper for input types that distinguishes present-null from omitted, enabling partial-update mutations.
- Kotlin extensions for `GraphQlClient` and `GraphQlTester` with reified type parameters.
- SSE (Server-Sent Events) transport as an HTTP-based subscription alternative to WebSocket.
