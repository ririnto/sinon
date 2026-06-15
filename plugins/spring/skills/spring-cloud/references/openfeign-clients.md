# Spring Cloud OpenFeign clients

Open this reference when the ordinary ConfigData-discovery-loadbalancer path in [`SKILL.md`](../SKILL.md) is not enough and the task is specifically about declarative OpenFeign clients.

## OpenFeign blocker

Use OpenFeign when a declarative HTTP client is clearer than direct `RestClient` or `WebClient` code.

```java
@EnableFeignClients
@SpringBootApplication
class ClientApplication {
}

@FeignClient(name = "inventory-service")
interface InventoryClient {
    @GetMapping("/api/items/{sku}")
    ItemDto find(@PathVariable String sku);
}
```

Keep the client name aligned with the logical service id and keep Feign-specific policy separate from gateway policy.

## FeignClientBuilder

Use `FeignClientBuilder` when client configuration must be constructed programmatically instead of declared with `@FeignClient`:

```java
@Bean
InventoryClient inventoryClient(FeignClientBuilder builder) {
    return builder.forType(InventoryClient.class)
        .url("http://inventory-service")
        .build();
}
```

## Singleton Client requirement

`FeignClient` instances must be singleton beans.
When a non-singleton scope is used, underlying HTTP connections are not shared, leading to resource exhaustion.
Keep the default singleton scope unless the use case explicitly demands per-instance isolation.

## Gotchas

- Do not use `@Scope("prototype")` on `@FeignClient` beans; the HTTP connection pool will leak.
- Encoder and Decoder beans are auto-configured in Boot 4.x (5.0.2+); missing bean errors in earlier versions are resolved.

## Validation rule

Verify the client reaches the intended logical service id and that fallback or error behavior matches the remote-call contract.
