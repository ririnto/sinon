---
name: "spring-pulsar"
description: "Use this skill when building Apache Pulsar producers, consumers, or readers in Spring with `PulsarTemplate`, `@PulsarListener`, schema mapping, subscription type and DLQ decisions, customizers, transactions, or Pulsar-specific testing and administration."
metadata:
  title: "Spring for Apache Pulsar"
  official_project_url: "https://spring.io/projects/spring-pulsar"
  reference_doc_urls:
    - "https://docs.spring.io/spring-pulsar/reference/"
  version: "2.0.4"
---

Use this skill when building Apache Pulsar producers, consumers, or readers in Spring with `PulsarTemplate`, `@PulsarListener`, schema mapping, subscription type and DLQ decisions, customizers, transactions, or Pulsar-specific testing and administration.

## Boundaries

Use `spring-pulsar` for Pulsar producer and consumer code, listeners, readers, topic naming, partitions, and Pulsar-specific admin or transaction decisions.

- Use `spring-kafka` or `spring-amqp` for Kafka or RabbitMQ semantics.
- Keep transport concerns in Pulsar-facing services and listeners. Domain logic should not know about subscriptions or topic partitions.

## Common path

The ordinary Spring Pulsar job is:

1. Define the topic name, subscription name, and subscription mode first.
2. Add the Pulsar starter and keep message format explicit.
3. Choose the schema strategy before the first producer and listener are written.
4. Publish through `PulsarTemplate` and consume through `@PulsarListener`.
5. Decide concurrency, retry, and dead-letter behavior before production rollout.
6. Add an integration test that proves the producer, listener, and topic configuration agree on payload shape and redelivery behavior.

## Dependency baseline

Use the Boot starter for ordinary Pulsar application code.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-pulsar</artifactId>
    </dependency>
</dependencies>
```

## First safe configuration

### Basic Pulsar properties

```yaml
spring:
  pulsar:
    client:
      service-url: pulsar://localhost:6650
    producer:
      topic-name: shipments
    consumer:
      subscription:
        name: warehouse
```

Open [references/client-authentication-and-tls.md](references/client-authentication-and-tls.md) when the cluster requires authentication, TLS, or separate administration credentials.

### Topic naming shape

```text
persistent://public/default/shipments
```

Start with explicit topic and subscription names in one namespace. Add tenant or namespace abstraction only when the deployment truly requires it.

## Coding procedure

1. Keep topic names, subscription names, and schema choices explicit and stable.
2. Use one schema mapping strategy per topic unless interop requires otherwise.
3. Pick the subscription type deliberately because it changes concurrency, ordering, and DLQ behavior.
4. Keep listener methods idempotent because redelivery can occur.
5. Decide dead-letter, retry, and redelivery policy before increasing listener parallelism.
6. Use readers only when the use case is genuinely cursor-style replay or audit traversal rather than normal subscription consumption.
7. Add producer or consumer customizers only when properties are not enough.
8. Test both happy-path delivery and one representative failure path with the same schema and subscription settings used in production.

## Implementation examples

### Producer and listener

```java
@Service
class ShipmentPublisher {
    private final PulsarTemplate<ShipmentEvent> pulsar;

    ShipmentPublisher(PulsarTemplate<ShipmentEvent> pulsar) {
        this.pulsar = pulsar;
    }

    void publish(ShipmentEvent event) {
        pulsar.send("shipments", event);
    }
}

@Component
class ShipmentListener {
    private final ShipmentService service;

    ShipmentListener(ShipmentService service) {
        this.service = service;
    }

    @PulsarListener(topics = "shipments", subscriptionName = "warehouse")
    void handle(ShipmentEvent event) {
        service.handle(event);
    }
}
```

### Schema and subscription shape

```java
@PulsarListener(
    topics = "shipments",
    subscriptionName = "warehouse",
    schemaType = SchemaType.JSON,
    subscriptionType = SubscriptionType.Shared,
    concurrency = "3"
)
void handle(ShipmentEvent event) {
    service.handle(event);
}
```

### Reader shape

```java
@PulsarReader(topics = "shipments", startMessageId = "earliest")
void replay(ShipmentEvent event) {
    audit.record(event);
}
```

### Transactional intent shape

```java
pulsar.executeInTransaction(operations -> {
    operations.send("shipments", event);
    return null;
});
```

Use transactions only when the workflow truly needs grouped Pulsar writes.

## Output and configuration shapes

### Topic shape

```text
shipments
persistent://public/default/shipments
```

### Subscription shape

```text
warehouse
```

### Listener declaration shape

```java
@PulsarListener(topics = "shipments", subscriptionName = "warehouse")
```

### Dead-letter policy shape

```java
DeadLetterPolicy.builder()
    .maxRedeliverCount(5)
    .deadLetterTopic("shipments-dlt")
    .build();
```

## Output contract

Return:

1. The producer, listener, and schema or subscription choice
2. Any topic, subscription, retry, or DLQ settings that must be configured
3. The validation path used, including the integration-test approach
4. Any remaining operational risks around redelivery, concurrency, or schema compatibility

## Integration test shape

```java
@SpringBootTest
@Testcontainers
class ShipmentFlowTest {
    static CountDownLatch deliveries = new CountDownLatch(1);
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
    void sendsAndReceivesJsonPayload() throws Exception {
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
    }
}
```

Pin a concrete broker image only when the test must prove compatibility with a chosen Pulsar line. Otherwise, keep the image aligned with one of the supported Pulsar lines for Spring Pulsar 2.0.x.

## Testing checklist

- Verify producer and consumer agree on topic name and payload shape.
- Verify subscription behavior matches the intended processing model and concurrency setting.
- Verify retry, redelivery, or DLQ behavior on one representative failure path.
- Verify reader-style code is used only where replay semantics are required.
- Verify namespace and topic resolution stay aligned with deployment configuration.
- Verify schema, customizer, and property overrides match the deployment configuration used in tests.

## Production checklist

- Keep topic and subscription names stable after other services depend on them.
- Make listeners idempotent because redelivery and replay can happen.
- Distinguish normal subscriptions from replay readers operationally.
- Keep dead-letter or retry topic handling observable.
- Keep schema changes compatible with existing producers and consumers.
- Prefer documented properties first, then customizers, then direct client-level overrides.
- Add transactions only when grouped write semantics are truly required.

## References

- Open [references/client-authentication-and-tls.md](references/client-authentication-and-tls.md) when the cluster requires authentication, TLS, or separate administration credentials.
- Open [references/reactive-pulsar.md](references/reactive-pulsar.md) when the application uses the reactive Pulsar programming model instead of imperative templates and listeners.
- Open [references/batch-consumption.md](references/batch-consumption.md) when the listener should consume batches instead of one message at a time.
- Open [references/schema-mapping-and-compatibility.md](references/schema-mapping-and-compatibility.md) when schema type, schema evolution, or message conversion compatibility is unclear.
- Open [references/producer-consumer-customizers-and-properties.md](references/producer-consumer-customizers-and-properties.md) when `spring.pulsar.*` properties are not enough or builder customizers are required.
- Open [references/consumer-acknowledgment.md](references/consumer-acknowledgment.md) when listener acknowledgment should be controlled explicitly instead of using the ordinary listener flow.
- Open [references/subscription-types-and-concurrency.md](references/subscription-types-and-concurrency.md) when subscription mode or listener concurrency must be chosen.
- Open [references/dead-letter-policy.md](references/dead-letter-policy.md) when DLQ behavior must be chosen explicitly.
- Open [references/error-handling-redelivery-and-dlt.md](references/error-handling-redelivery-and-dlt.md) when redelivery, backoff, or dead-letter publishing behavior must be tuned.
- Open [references/readers-and-replay.md](references/readers-and-replay.md) when the task needs replay readers.
- Open [references/partitioned-topics-and-key-routing.md](references/partitioned-topics-and-key-routing.md) when the task needs partitioned topics or key-based routing decisions.
- Open [references/admin-and-topic-provisioning.md](references/admin-and-topic-provisioning.md) when the application must create topics, partitions, or namespace-level settings programmatically.
- Open [references/transactions-and-coordinated-ack.md](references/transactions-and-coordinated-ack.md) when grouped writes or acknowledgment coordination are required.
- Open [references/tombstones.md](references/tombstones.md) when tombstone records are required.
- Open [references/testing-with-testcontainers.md](references/testing-with-testcontainers.md) when Pulsar integration tests need Testcontainers or admin-backed verification.
- Open [references/observability.md](references/observability.md) when metrics, tracing, or production debugging must be added.
