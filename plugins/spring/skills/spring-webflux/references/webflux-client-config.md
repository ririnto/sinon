---
title: Spring WebFlux Client and Codec Recipes
description: >-
  Reference for WebFlux client wiring, selective customization, and implementation-specific DNS configuration.
---

Use this reference when the problem is outbound reactive HTTP behavior rather than endpoint structure.

## Case 1: Shared `WebClient` defaults

Use this case when the same default headers or status handling rules should apply broadly.

```java
@Bean
WebClientCustomizer webClientCustomizer() {
    return builder -> builder
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultStatusHandler(HttpStatusCode::isError, response -> Mono.error(new RemoteCallException()));
}
```

## Case 2: No extra customization

Use this case when the shared policy is already enough.

```java
class RemoteClient {
    private final WebClient webClient;

    RemoteClient(WebClient webClient) {
        this.webClient = webClient;
    }
}
```

## Case 3: One client needs extra customization

Use this case only at the integration boundary.

```java
class ExternalApiClient {
    private final WebClient webClient;

    ExternalApiClient(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("https://api.example.org")
                .filter((request, next) -> next.exchange(request))
                .build();
    }
}
```

## Case 4: Shared filter policy

Use this case when authentication, tracing, or request logging should apply broadly.

```java
@Bean
WebClientCustomizer webClientCustomizer() {
    return builder -> builder.filter((request, next) -> next.exchange(request));
}
```

## Case 5: Request-level customization

Use this case when a single request needs extra headers or auth without changing the client setup.

```java
Mono<RemoteDto> fetch(WebClient client) {
    return client.get()
            .uri("/items")
            .headers(headers -> headers.setBearerAuth("token"))
            .retrieve()
            .bodyToMono(RemoteDto.class);
}
```

## Case 6: Error mapping at the call site

Use this case when one remote call needs explicit mapping beyond shared defaults.

```java
Mono<RemoteDto> fetch(WebClient client) {
    return client.get()
            .uri("/items")
            .retrieve()
            .onStatus(HttpStatusCode::isError, response -> Mono.error(new RemoteCallException()))
            .bodyToMono(RemoteDto.class);
}
```

## Case 7: Typed HTTP interface

Use this case when the remote dependency should read like a small typed client.

```java
interface OrderHttpApi {

    @GetExchange("/orders/{id}")
    Mono<OrderResponse> findOne(@PathVariable Long id);
}

@Bean
OrderHttpApi orderHttpApi(WebClient webClient) {
    HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(WebClientAdapter.create(webClient))
            .build();
    return factory.createClient(OrderHttpApi.class);
}
```

## Case 8: Shared low-level DNS policy by implementation

Use these cases only when DNS behavior must be standardized globally.

### Reactor

```java
@Bean
@Order(10)
ClientHttpConnectorBuilderCustomizer<ReactorClientHttpConnectorBuilder> reactorDnsCustomizer() {
    return connectorBuilder -> connectorBuilder.withHttpClientFactory(httpClientBuilder ->
            httpClientBuilder.withCustomizer(httpClient ->
                    httpClient.resolver(spec -> spec
                            .queryTimeout(Duration.ofMillis(500))
                            .cacheMinTimeToLive(Duration.ofSeconds(5))
                            .cacheMaxTimeToLive(Duration.ofMinutes(5))
                            .cacheNegativeTimeToLive(Duration.ofSeconds(1))
                            .maxQueriesPerResolve(8)
                            .ndots(1))));
}
```

### JDK

```java
@Bean
@Order(20)
ClientHttpConnectorBuilderCustomizer<JdkClientHttpConnectorBuilder> jdkDnsCustomizer(InetAddressResolver resolver) {
    return connectorBuilder -> connectorBuilder.withHttpClientCustomizer(httpClient ->
            httpClient.resolver(resolver));
}
```

### Jetty

```java
@Bean
@Order(30)
ClientHttpConnectorBuilderCustomizer<JettyClientHttpConnectorBuilder> jettyDnsCustomizer(SocketAddressResolver resolver) {
    return connectorBuilder -> connectorBuilder.withHttpClientCustomizer(httpClient ->
            httpClient.setSocketAddressResolver(resolver));
}
```

### HttpComponents

```java
@Bean
@Order(40)
ClientHttpConnectorBuilderCustomizer<HttpComponentsClientHttpConnectorBuilder> httpComponentsDnsCustomizer(DnsResolver dnsResolver) {
    return connectorBuilder -> connectorBuilder.withConnectionManagerCustomizer(connectionManager ->
            connectionManager.setDnsResolver(dnsResolver));
}
```

## Case 9: Streaming responses

Use this case when the endpoint is a real stream rather than a finite list.

```java
@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
Flux<EventResponse> events() {
    return service.stream();
}
```

## Case 10: Blocking boundary

Use this rule when a dependency is blocking underneath a reactive surface.

- keep blocking work out of the reactive path if possible
- if it cannot be removed, isolate that adaptation explicitly

## Decision rule

- shared policy belongs in `WebClientCustomizer`, `ClientHttpConnectorBuilderCustomizer`, or a higher configurer layer
- inject `WebClient` directly when no extra customization is needed
- inject `WebClient.Builder` only when one integration needs extra behavior
- apply low-level DNS policy through the implementation-specific connector customizer lane when it must be shared globally
