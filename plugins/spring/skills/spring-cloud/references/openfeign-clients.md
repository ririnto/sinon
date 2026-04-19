# Spring Cloud OpenFeign clients

Open this reference when the ordinary ConfigData-discovery-loadbalancer path in [SKILL.md](../SKILL.md) is not enough and the task is specifically about declarative OpenFeign clients.

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

## Validation rule

Verify the client reaches the intended logical service id and that fallback or error behavior matches the remote-call contract.
