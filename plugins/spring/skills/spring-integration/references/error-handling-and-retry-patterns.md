# Spring Integration error handling and retry patterns

Open this reference when the flow needs endpoint advice, retry, circuit breaking, or explicit error-channel routing.

## Retry advice shape

```java
@Bean
RequestHandlerRetryAdvice requestHandlerRetryAdvice() {
    RequestHandlerRetryAdvice advice = new RequestHandlerRetryAdvice();
    RetryTemplate template = new RetryTemplate();
    advice.setRetryTemplate(template);
    advice.setRecoveryCallback(new ErrorMessageSendingRecoverer(errorChannel()));
    return advice;
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

## Stateful versus stateless retry

- Use stateless retry for idempotent handlers that can safely repeat work.
- Use stateful retry only when message identity and recovery need to survive repeated attempts with the same state key.
