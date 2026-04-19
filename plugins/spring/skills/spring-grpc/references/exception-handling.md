# Spring gRPC exception handling

Open this reference when exception-to-status mapping should be reused across multiple handlers, customized per service, or expressed with Spring gRPC's handler APIs instead of inline `Status` throws.

## Choose the mapping style

| Situation | Use |
| --- | --- |
| One handler has one obvious validation failure | inline `Status` mapping in the service method |
| The same exception rule appears in several handlers | `GrpcExceptionHandler` bean |
| Mapping should live next to one service or advice component | `@GrpcExceptionHandler` |
| Exception should fall through to default handling | return `null` from the handler |

## Reusable handler bean

```java
@Bean
GrpcExceptionHandler<IllegalArgumentException, HelloRequest> invalidArgumentHandler() {
    return (exception, request) -> Status.INVALID_ARGUMENT
        .withDescription(exception.getMessage());
}
```

## Advice-style handler

```java
@GrpcAdvice
class GreetingAdvice {
    @GrpcExceptionHandler
    Status handleIllegalArgument(IllegalArgumentException exception) {
        return Status.INVALID_ARGUMENT.withDescription(exception.getMessage());
    }
}
```

## Guardrails

- Keep transport status mapping near the gRPC boundary instead of leaking protobuf or gRPC types into application services.
- Prefer explicit status choices over generic internal errors for validation, authorization, and not-found cases.
- Return `null` only when you intentionally want Spring gRPC to continue with its default exception handling.
