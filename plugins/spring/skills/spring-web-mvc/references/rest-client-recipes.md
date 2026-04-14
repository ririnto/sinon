---
title: Spring Web MVC Client Recipes
description: >-
  RestClient customization lanes, typed HTTP interfaces, and per-implementation DNS policy.
---

Use this reference when outbound HTTP client wiring, customization levels, or DNS policy decisions are needed.

## Case 1: Shared `RestClient` defaults

Use this case when default headers or status handling should apply broadly.

```java
@Bean
RestClientCustomizer restClientCustomizer() {
    return builder -> builder
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                throw new RemoteCallException();
            });
}
```

## Case 2: One client needs extra customization

Use this case only at the integration boundary.

```java
class OrderApiClient {
    private final RestClient restClient;

    OrderApiClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("https://orders.example.org")
                .defaultHeader("X-Client", "orders-service")
                .build();
    }
}
```

## Case 3: Request-level customization

Use this case when one request needs extra headers or auth without changing the client setup.

```java
OrderResponse findOne(RestClient restClient, Long id, String token) {
    return restClient.get()
            .uri("/orders/{id}", id)
            .headers(headers -> headers.setBearerAuth(token))
            .retrieve()
            .body(OrderResponse.class);
}
```

## Case 4: Shared interceptor policy

Use this case when authentication, correlation, or request logging should apply broadly.

```java
@Bean
RestClientCustomizer restClientCustomizer() {
    return builder -> builder.requestInterceptor((request, body, execution) -> {
        request.getHeaders().set("X-Client", "orders-service");
        return execution.execute(request, body);
    });
}
```

## Case 5: Typed HTTP interface

Use this case when the remote dependency should read like a small typed client.

```java
interface OrderHttpApi {

    @GetExchange("/orders/{id}")
    OrderResponse findOne(@PathVariable Long id);
}

@Bean
OrderHttpApi orderHttpApi(RestClient restClient) {
    HttpServiceProxyFactory factory = HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build();
    return factory.createClient(OrderHttpApi.class);
}
```

## Case 6: Shared low-level DNS policy by implementation

Use these cases only when DNS behavior must be standardized globally.

### Reactor

```java
@Bean
@Order(10)
ClientHttpRequestFactoryBuilderCustomizer<ReactorClientHttpRequestFactoryBuilder> reactorDnsCustomizer() {
    return factoryBuilder -> factoryBuilder.withHttpClientFactory(httpClientBuilder ->
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
ClientHttpRequestFactoryBuilderCustomizer<JdkClientHttpRequestFactoryBuilder> jdkDnsCustomizer(InetAddressResolver resolver) {
    return factoryBuilder -> factoryBuilder.withHttpClientCustomizer(httpClient ->
            httpClient.resolver(resolver));
}
```

### Jetty

```java
@Bean
@Order(30)
ClientHttpRequestFactoryBuilderCustomizer<JettyClientHttpRequestFactoryBuilder> jettyDnsCustomizer(SocketAddressResolver resolver) {
    return factoryBuilder -> factoryBuilder.withHttpClientCustomizer(httpClient ->
            httpClient.setSocketAddressResolver(resolver));
}
```

### HttpComponents

```java
@Bean
@Order(40)
ClientHttpRequestFactoryBuilderCustomizer<HttpComponentsClientHttpRequestFactoryBuilder> httpComponentsDnsCustomizer(DnsResolver dnsResolver) {
    return factoryBuilder -> factoryBuilder.withConnectionManagerCustomizer(connectionManager ->
            connectionManager.setDnsResolver(dnsResolver));
}
```

### Simple client

`SimpleClientHttpRequestFactoryBuilder` relies on JVM and system-level DNS behavior. There is no equivalent per-builder DNS customization hook here.

## Case 7: Request-factory decision rule

Use request-factory customization when shared low-level behavior such as DNS policy must be controlled per implementation.

- Shared policy belongs in `RestClientCustomizer`, `ClientHttpRequestFactoryBuilderCustomizer`, or a higher configurer layer.
- Inject `RestClient` directly when no extra customization is needed.
- Inject `RestClient.Builder` only when one integration needs extra behavior.
- Apply low-level DNS policy through the implementation-specific request-factory customizer lane when it must be shared globally.

The canonical `RestClient` template belongs in the parent skill entrypoint, not as a local reference link.
