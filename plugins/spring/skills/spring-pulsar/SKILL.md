---
metadata:
  reference:
    Spring Pulsar:
      version: 2.0.7
      url:
        - https://docs.spring.io/spring-pulsar/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/pulsar/spring-pulsar/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/pulsar/spring-pulsar/2.0.7/spring-pulsar-2.0.7.pom
    Spring Boot Dependency BOM:
      version: 4.0.8
      url:
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.0.8/spring-boot-dependencies-4.0.8.pom
    Pulsar Docker Image:
      version: 4.1.3
      url: https://hub.docker.com/r/apachepulsar/pulsar/tags?name=4.1.3
    Spring Pulsar Compatibility Matrix:
      url: https://docs.spring.io/spring-pulsar/reference/appendix/version-compatibility.html
name: spring-pulsar
description: >-
  Use Spring Pulsar for producers, listeners, schemas, subscriptions, acknowledgments, replay, and dead letters.
---

# Spring for Apache Pulsar

## Compatibility

Do not treat a BOM-managed combination as supported unless the Spring Pulsar compatibility matrix lists its release lines.
For the documented Boot-managed version pairing, read [the compatibility example](references/compatibility-example.md).

## Boundaries

Use `spring-pulsar` for Pulsar producer and consumer code, listeners, readers, topic naming, partitions, and Pulsar-specific admin or transaction decisions.

- Kafka or RabbitMQ producer and consumer semantics are outside this skill's scope.
- Keep transport concerns in Pulsar-facing services and listeners.
  - Domain logic should not know about subscriptions or topic partitions.

## Dependency baseline

Before recommending a new version, check each selected Maven artifact on Maven Central for its latest stable release compatible with the project's Boot and Framework lines.
Keep project BOM or version-catalog management and existing pins unless the task authorizes changing them.

Use the Boot starter for ordinary Pulsar application code.

For the Boot starter declaration, see [the dependency example](references/configuration-examples.md#dependency-example).

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

Start with explicit topic and subscription names in one namespace.
Add tenant or namespace abstraction only when the deployment truly requires it.

## Coding procedure

1. Keep topic names, subscription names, and schema choices explicit and stable.
2. Use one schema mapping strategy per topic unless interop requires otherwise.
3. Pick the subscription type deliberately because it changes concurrency, ordering, and DLQ behavior.
4. Keep listener methods idempotent because redelivery can occur.
5. Decide dead-letter, retry, and redelivery policy before increasing listener parallelism.
6. Use readers only when the use case is cursor-style replay or audit traversal rather than normal subscription consumption.
7. Add producer or consumer customizers only when properties are not enough.
8. Choose acknowledgment mode (BATCH, RECORD, MANUAL) based on whether the listener needs per-message or per-batch ack control.
9. Use PulsarConsumerErrorHandler for Spring-native DLQ with non-Shared subscriptions, and it is also valid with Shared subscriptions when Spring-native recovery is preferred.
10. For delivery changes, test success and affected failure paths with the production schema and subscription settings.

## Implementation examples

- For producer and listener, see [the example](references/implementation-examples.md#producer-and-listener).
- For schema and subscription shape, see [the example](references/implementation-examples.md#schema-and-subscription-shape).
- For reader shape, see [the example](references/implementation-examples.md#reader-shape).
- For transactional intent shape, see [the example](references/implementation-examples.md#transactional-intent-shape).

## Topic and listener shapes

For topic names or patterns, subscriptions, listener acknowledgments, or dead-letter policy configuration, read [topic and listener shapes](references/topic-and-listener-shapes.md).

## Integration test shape

The [integration test example](references/configuration-examples.md#example-1-integration-test-shape) pins broker 4.1.3 to the Boot 4.0.8 managed client.
Change it only to test another client line in the compatibility matrix; check the image registry and matrix before selecting a different tag.

## Testing checklist

Select checks for affected delivery contracts and regression risks.

- Verify producer and consumer agree on topic name and payload shape.
- Verify subscription behavior matches the intended processing model and concurrency setting.
- When retry, redelivery, or DLQ is configured, verify it on one representative failure path.
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

Read the relevant reference when the named condition applies:

- [Batch consumption](references/batch-consumption.md): consume batches rather than individual messages.
- [Schema mapping](references/schema-mapping-and-compatibility.md): schema type, evolution, or message conversion compatibility.
- [Customizers and properties](references/producer-consumer-customizers-and-properties.md): `spring.pulsar.*` is insufficient or builder customizers are required.
- [Acknowledgment](references/consumer-acknowledgment.md): explicit control rather than ordinary listener flow.
- [Subscriptions and concurrency](references/subscription-types-and-concurrency.md): choose subscription mode or listener concurrency.
- [Dead-letter policy](references/dead-letter-policy.md): explicit DLQ selection.
- [Error handling](references/error-handling-redelivery-and-dlt.md): tune redelivery, backoff, or dead-letter publishing.
- [Readers and replay](references/readers-and-replay.md): replay readers.
- [Partitions and keys](references/partitioned-topics-and-key-routing.md): partitioned topics or key routing.
- [Provisioning](references/admin-and-topic-provisioning.md): programmatically create topics, partitions, or namespace settings.
- [Transactions](references/transactions-and-coordinated-ack.md): grouped writes or coordinated acknowledgments.
- [Tombstones](references/tombstones.md): tombstone records.
- [Testcontainers](references/testing-with-testcontainers.md): Pulsar integration tests or admin-backed verification.
- [Observability](references/observability.md): metrics, tracing, or production debugging.
- [Headers](references/headers.md): access or map Pulsar message headers.
- [Container lifecycle](references/container-lifecycle.md): pause, resume, or handle listener startup failures.
