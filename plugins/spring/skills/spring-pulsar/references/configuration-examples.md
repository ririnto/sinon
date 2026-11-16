# Configuration and test examples

## Example 1: Integration test shape

```java
@SpringBootTest
@Testcontainers
class ShipmentFlowTest {
    static CountDownLatch deliveries = new CountDownLatch(1);
    static AtomicReference<ShipmentEvent> received = new AtomicReference<>();

    @Container
    static PulsarContainer pulsar = new PulsarContainer("apachepulsar/pulsar:4.1.3");

    @DynamicPropertySource
    static void pulsarProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.pulsar.client.service-url", pulsar::getPulsarBrokerUrl);
        registry.add("spring.pulsar.admin.service-url", pulsar::getHttpServiceUrl);
    }

    @Autowired
    PulsarTemplate<ShipmentEvent> pulsarTemplate;

    @Test
    void sendsAndReceivesJsonPayload() throws Exception {
        pulsarTemplate.send("shipments", new ShipmentEvent("shipment-42"));
        assertAll(() -> assertThat(deliveries.await(10, TimeUnit.SECONDS)).isTrue(), () -> assertThat(received.get().shipmentId()).isEqualTo("shipment-42"));
    }

    @Component
    static class TestListener {
        @PulsarListener(topics = "shipments", subscriptionName = "warehouse-test", schemaType = SchemaType.JSON)
        void handle(ShipmentEvent event) {
            received.set(event);
            deliveries.countDown();
        }
    }
}
```

## Dependency example

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-pulsar</artifactId>
    </dependency>
</dependencies>
```
