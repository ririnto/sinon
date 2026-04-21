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

```java
@Testcontainers
@SpringBootTest
class ShipmentFlowTest {
    static CountDownLatch deliveries = new CountDownLatch(1);
    static CountDownLatch deadLetterDeliveries = new CountDownLatch(1);
    static AtomicReference<ShipmentEvent> received = new AtomicReference<>();

    @Container
    static PulsarContainer pulsar = new PulsarContainer("apachepulsar/pulsar:3.3.2");

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
        assertAll(
            () -> assertThat(deliveries.await(10, TimeUnit.SECONDS)).isTrue(),
            () -> assertThat(received.get().shipmentId()).isEqualTo("shipment-42")
        );
    }

    @Component
    static class TestListener {
        @PulsarListener(topics = "shipments", subscriptionName = "warehouse-test", schemaType = SchemaType.JSON)
        void handle(ShipmentEvent event) {
            received.set(event);
            deliveries.countDown();
        }

        @PulsarListener(topics = "shipments-dlt", subscriptionName = "warehouse-dlt-test", schemaType = SchemaType.JSON)
        void handleDeadLetter(ShipmentEvent event) {
            deadLetterDeliveries.countDown();
        }
    }
}
```

## Verification rule

- Test the same topic, schema, subscription type, and retry or DLQ settings used in production.
- Use admin-backed verification when the assertion depends on topic provisioning or partition count.
- Keep one failure-path test that proves the listener reaches the intended redelivery or dead-letter behavior.
- Keep the broker image aligned with one of the supported Pulsar lines for the Spring Pulsar line in use.

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

## Failure-path shape

```java
@Test
void verifiesDeadLetterRouting() throws Exception {
    pulsarTemplate.send("shipments", new ShipmentEvent("poison"));
    assertThat(deadLetterDeliveries.await(10, TimeUnit.SECONDS)).isTrue();
}
```
