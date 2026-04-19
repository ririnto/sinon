# Spring Pulsar admin and topic provisioning

Open this reference when the application must create topics, partitions, or namespace-level settings programmatically.

## Topic provisioning shape

```java
@Bean
ApplicationRunner provisionTopics(PulsarAdministration administration) {
    return args -> administration.createOrModify(
        PulsarTopic.builder("persistent://public/default/shipments")
            .numberOfPartitions(8)
            .build()
    );
}
```

## Provisioning rules

- Provision topics in startup code only when the service genuinely owns those topics.
- Keep tenant, namespace, partition count, and retention rules explicit in code or configuration.
- Separate admin privileges from ordinary producer and consumer credentials when possible.

## Good fit

- Service-owned internal topics
- Local development bootstrap
- Deterministic integration test setup

## Poor fit

- Shared platform topics owned by another team
- Runtime flows that mutate broker policy on demand
