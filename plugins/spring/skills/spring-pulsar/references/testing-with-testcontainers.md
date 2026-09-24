# Spring Pulsar testing with Testcontainers

Open this reference when Pulsar integration tests need Testcontainers or admin-backed verification.

## Dependency hint

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-pulsar</artifactId>
    <scope>test</scope>
</dependency>
```

## Testcontainers shape

The `4.1.3` broker image below illustrates the documented Boot 4.0.8 and Pulsar client pairing.
Before selecting a different image tag, check the image registry and Spring Pulsar compatibility matrix.

```java
@Testcontainers
@SpringBootTest
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
    void verifiesDeliveryPath() throws Exception {
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

## Verification rule

- Test the same topic, schema, subscription type, and retry or DLQ settings used in production.
- Use admin-backed verification when the assertion depends on topic provisioning or partition count.
- Add a failure-path test only when retry or DLQ recovery is configured, and assert the intended redelivery or dead-letter behavior.
- Keep the broker image aligned with a Pulsar client line listed in the Spring Pulsar compatibility matrix.
  - Spring Boot 4.0.8 manages Spring Pulsar 2.0.7 with Pulsar client 4.1.3.
  - The matrix lists Spring Pulsar 2.0.x with Boot 4.0.x and Pulsar clients 4.1.x, 4.0.x, or 3.3.x.
  - Pin a specific image version only when the test must prove compatibility with a chosen Pulsar line.

## Admin verification shape

```java
@Autowired
PulsarAdministration administration;

@Test
void verifiesPartitionCount() throws Exception {
    int partitions = administration.createAdminClient().topics().getPartitionedTopicMetadata("persistent://public/default/shipments").partitions;
    assertThat(partitions).isEqualTo(3);
}
```
