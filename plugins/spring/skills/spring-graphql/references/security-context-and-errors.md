# Spring GraphQL security context and errors

Open this reference when the common path in `SKILL.md` is not enough and the task needs interceptor-based context propagation, authorization handling, or more explicit GraphQL error shaping.

## Security context propagation

Keep authentication and authorization decisions explicit at resolver or interceptor boundaries.

Do not assume ordinary web security context propagation always matches GraphQL execution semantics automatically.

## Interceptor boundary

Use a GraphQL interceptor when request metadata must be copied into the GraphQL context before controller methods run.

```java
@Bean
WebGraphQlInterceptor correlationInterceptor() {
    return (request, chain) -> {
        String correlationId = request.getHeaders().getFirst("X-Correlation-Id");
        if (correlationId != null) {
            request.configureExecutionInput((input, builder) -> builder.graphQLContext(Map.of("correlationId", correlationId)).build());
        }
        return chain.next(request);
    };
}
```

Read the value from `GraphQLContext` in controller methods or downstream components only when the request really needs it.

## Error categories

Keep GraphQL errors intentional and predictable.

- validation or input failures
- authorization failures
- internal execution failures

Those categories often need different client handling and observability.

## Exception shaping

```java
@ControllerAdvice
class GraphQlExceptionAdvice {
    @GraphQlExceptionHandler
    GraphQLError handle(AccessDeniedException ex) {
        return GraphQLError.newError().message("forbidden").errorType(ErrorType.FORBIDDEN).build();
    }
}
```

Use explicit exception shaping when clients need stable error categories instead of transport-specific failure details.

## Decision points

| Situation | Use |
| --- | --- |
| Request metadata must reach resolvers | interceptor-based context propagation |
| Access rules differ by field or operation | explicit authorization at resolver or service boundary |
| Clients need predictable failure handling | stable error categorization |
