# Spring Pulsar testing with Testcontainers

Open this reference when Pulsar integration tests need Testcontainers or admin-backed verification.

## Testcontainers shape

```java
@Testcontainers
@SpringBootTest
class ShipmentFlowTest {
    @Container
    static PulsarContainer pulsar = new PulsarContainer("apachepulsar/pulsar:3.3.2");

    @DynamicPropertySource
    static void pulsarProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.pulsar.client.service-url", pulsar::getPulsarBrokerUrl);
        registry.add("spring.pulsar.admin.service-url", pulsar::getHttpServiceUrl);
    }
}
```

## Verification rule

- Test the same topic, schema, subscription type, and retry or DLQ settings used in production.
- Use admin-backed verification when the assertion depends on topic provisioning or partition count.
- Keep one failure-path test that proves the listener reaches the intended redelivery or dead-letter behavior.

## Admin verification shape

```java
@Autowired
PulsarAdministration administration;

@Test
void verifiesPartitionCount() throws Exception {
    int partitions = administration.createAdminClient().topics().getPartitionedTopicMetadata(
        "persistent://public/default/shipments"
    ).partitions;
    assertThat(partitions).isEqualTo(3);
}
```
