# Spring Framework WebClient and reactive depth

Open this reference when the common path in `SKILL.md` is not enough and the blocker is client filters, transport-specific timeouts, retry selection, or deeper reactive pipeline behavior.

## ExchangeFilterFunction for cross-cutting concerns

Use an `ExchangeFilterFunction` for correlation IDs, auth headers, or request/response logging:

```java
@Bean
WebClient webClient(WebClient.Builder builder) {
    return builder
        .baseUrl("https://api.example.com")
        .filter((request, next) -> {
            ClientRequest filtered = ClientRequest.from(request)
                .header("X-Correlation-Id", UUID.randomUUID().toString())
                .build();
            return next.exchange(filtered);
        })
        .build();
}
```

## Reactor Netty-specific timeouts

Configure response timeout on the Reactor Netty client when the application uses that transport:

```java
HttpClient httpClient = HttpClient.create()
    .responseTimeout(Duration.ofSeconds(5));

ClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

WebClient client = WebClient.builder()
    .baseUrl("https://api.example.com")
    .clientConnector(connector)
    .build();
```

Per-request timeout:

```java
Mono<Order> order = client.get()
    .uri("/orders/{id}", orderId)
    .httpRequest(httpRequest -> {
        HttpClientRequest reactorRequest = httpRequest.getNativeRequest();
        reactorRequest.responseTimeout(Duration.ofSeconds(5));
    })
    .retrieve()
    .bodyToMono(Order.class);
```

Label these snippets as Reactor Netty-specific. Other WebClient transports have different timeout APIs.

## Retry only transient failures

Retry the reactive chain selectively rather than retrying every `WebClientResponseException`:

```java
Mono<Order> order = client.get()
    .uri("/orders/{id}", orderId)
    .retrieve()
    .bodyToMono(Order.class)
    .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
        .filter(ex -> ex instanceof WebClientResponseException response && response.getStatusCode().is5xxServerError()));
```

Do not retry 4xx responses unless the upstream contract explicitly marks them as retryable.

## Reactive pipeline depth

Keep the pipeline non-blocking. Prefer composition operators over `block()` in request-handling code:

```java
Mono<OrderSummary> summary = client.get()
    .uri("/orders/{id}", orderId)
    .retrieve()
    .bodyToMono(Order.class)
    .flatMap(order -> pricingClient.quote(order.sku()).map(quote -> new OrderSummary(order, quote)));
```

Use `WebTestClient` or Reactor `StepVerifier` to verify timeout, retry, and error-mapping behavior without falling back to blocking calls.
