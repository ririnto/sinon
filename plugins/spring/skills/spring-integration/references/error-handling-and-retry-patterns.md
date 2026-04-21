# Spring Integration error handling and retry patterns

Open this reference when the flow needs endpoint advice, retry, circuit breaking, or explicit error-channel routing.

## Retry advice shape

```java
import org.springframework.integration.handler.advice.ErrorMessageSendingRecoverer;
import org.springframework.core.retry.RetryTemplate;

@Bean
RequestHandlerRetryAdvice requestHandlerRetryAdvice() {
    RequestHandlerRetryAdvice advice = new RequestHandlerRetryAdvice();
    RetryTemplate template = new RetryTemplate();
    advice.setRetryTemplate(template);
    advice.setRecoveryCallback(new ErrorMessageSendingRecoverer(errorChannel()));
    return advice;
}
```

Spring Integration 7.0 moved retry support to Spring Framework Core retry APIs. Use `org.springframework.core.retry.RetryTemplate`, not the removed `org.springframework.retry` package.

## Advice attachment shape

```java
@Bean
IntegrationFlow orderFlow(RequestHandlerRetryAdvice requestHandlerRetryAdvice, OrderService service) {
    return IntegrationFlow.from("orders.input").handle(service, endpoint -> endpoint.advice(requestHandlerRetryAdvice)).get();
}
```

## Poller error channel shape

```java
Pollers.fixedDelay(Duration.ofSeconds(5))
    .errorChannel("integration.errors")
```

## Decision points

- Use handler advice when retries belong to one endpoint rather than to the entire transport boundary.
- Use an `errorChannel` when failures should become messages routed through the graph.
- Add a circuit breaker when the downstream system can stay unhealthy for long enough that repeated retries would only amplify pressure.

## Custom error channel shape

```java
@Bean
IntegrationFlow integrationErrors() {
    return IntegrationFlow.from("integration.errors")
        .handle(message -> logger.warn("integration failure", message.getPayload()))
        .get();
}
```

## Stateful versus stateless retry

- Use stateless retry for idempotent handlers that can safely repeat work.
- Use stateful retry only when message identity and recovery need to survive repeated attempts with the same state key.

## Recovery-path rule

Verify one representative exhausted-retry case reaches the intended error channel or recovery callback instead of disappearing at the endpoint boundary.
