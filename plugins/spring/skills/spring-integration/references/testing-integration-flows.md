# Spring Integration testing integration flows

Open this reference when the task needs `@SpringIntegrationTest`, `MockIntegrationContext`, `noAutoStartup`, or graph-level assertions.

## Graph-level test shape

```java
@SpringJUnitConfig(OrderIntegrationConfiguration.class)
@SpringIntegrationTest(noAutoStartup = {"fileInboundAdapter"})
class OrderFlowTests {
    @Autowired
    OrderGateway gateway;

    @Autowired
    @Qualifier("invalidOrders")
    QueueChannel invalidOrders;

    @Test
    void invalidOrderIsDiscarded() {
        gateway.submit(new OrderCommand("bad", "customer", List.of()));
        assertThat(invalidOrders.receive(1_000)).isNotNull();
    }
}
```

## MockIntegrationContext shape

```java
mockIntegrationContext.substituteMessageSourceFor("fileInboundAdapter", () -> message);
```

```java
mockIntegrationContext.resetBeans();
```

## Testing rules

- Keep one graph-level test for the ordinary path and one failure-path test for discard or error routing.
- Use `noAutoStartup` when inbound adapters or pollers would otherwise start background work during tests.
- Substitute sources or handlers only at the boundary being isolated. Leave the rest of the graph intact.

## Poller test shape

```java
mockIntegrationContext.substituteTriggerFor("fileInboundAdapter", new OnlyOnceTrigger());
```
