---
metadata:
  reference:
    Spring AMQP:
      version: 4.1.1
      url:
        - https://docs.spring.io/spring-amqp/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/amqp/spring-amqp-bom/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/amqp/spring-amqp-bom/4.1.1/spring-amqp-bom-4.1.1.pom
name: spring-amqp
description: >-
  Use Spring AMQP and RabbitMQ producers, listeners, topology, retries, and dead letters.
---

# Spring AMQP

The examples target the Spring AMQP 4.1.x line.

## Boundaries

Use `spring-amqp` for RabbitMQ-oriented producers, consumers, listener containers, queue and exchange topology, delivery retries, dead-letter flows, and RabbitMQ-specific operational seams.
The `spring-amqp-client` module provides generic AMQP 1.0 protocol support for any AMQP 1.0 peer, but it is a separate surface with its own `@EnableAmqp` / `@AmqpListener` annotations and distinct client APIs.

- Kafka or Pulsar semantics and client APIs are outside this skill's scope.
- Keep business logic outside listeners.
  - Listeners should deserialize, validate, and delegate.
- Keep queue, exchange, routing-key, and delivery-policy names stable once publishers and consumers are deployed.
- When deploying against RabbitMQ 4.3+, note that `queue-master-locator` is denied by default and delivery-count semantics changed -- retry and dead-letter policies may need adjustment.

## Surface routing

Read the applicable reference:

- Broker credentials or virtual hosts: [broker setup](references/broker-configuration-and-vhost-setup.md); delayed exchanges or broker events: [delayed delivery](references/delayed-exchange-and-broker-events.md).
- Request-reply: [request-reply](references/request-reply.md); pull consumption: [polling receive](references/polling-receive.md); confirms and returns: [publish reliability](references/publisher-confirms-returns-and-send-reliability.md).
- Listener signatures, headers, validation, or conversion: [listener methods](references/conversion-and-listener-method-signatures.md); container choice, prefetch, concurrency, or ordering: [containers](references/container-variants-and-concurrency.md).
- Recoverers, transactions, or failure classification: [retry and recovery](references/retry-recovery-and-transactions.md).
- Batch listeners: [batch](references/batch-listeners.md); async returns: [async](references/async-return-listeners.md); consumer threading: [back pressure](references/listener-threading-and-back-pressure.md).
- RabbitMQ streams: [stream variants](references/stream-variants.md); multiple brokers: [multi-broker](references/multi-broker-variants.md); generic AMQP 1.0: [AMQP 1.0 support](references/generic-amqp10-support.md).
- Listener or broker test harnesses: [testing](references/testing-support-and-listener-harnesses.md).
- Listener metrics: [Micrometer](references/listener-metrics-and-micrometer.md); publish/consume trace correlation: [tracing](references/distributed-tracing-for-amqp.md); delivery diagnosis: [debugging](references/delivery-debugging-checklist.md).

## Dependencies and delivery

Check Maven Central for the latest compatible stable release before recommending a new version; preserve existing BOM or catalog pins unless authorized to change them.
Use the Boot starter for application code and Rabbit test support for listener and broker tests.
Run only the needed application path with an authorized local broker.
Keep queue, exchange, binding, routing key, and delivery policy contracts explicit and stable.
Use one message converter strategy per module unless interoperability requires otherwise; the no-argument JSON converter trusts only `java.util` and `java.lang`, so provide application payload packages as trusted packages.
Use `RabbitTemplate` for send and receive and `@RabbitListener` for ordinary consumption; keep listeners narrow and idempotent and align payloads with the converter.
Use pull receive only for a synchronous consumer contract and set explicit request-reply timeouts when needed.
Publisher confirms report exchange acceptance, not routing; returns report mandatory unroutable messages and do not replace confirms.
A positive confirm can accompany a return.
Use confirms and returns when the producer must distinguish acceptance from unroutable delivery.
Use a dedicated listener factory only for distinct acknowledgment, retry, or concurrency rules.
Record listener duration, failure counts by listener or queue, and dead-letter or exhausted-message counts.
Choose one retry policy per consumer path; for idempotent listeners start with stateless retry, route exhausted messages to an explicit DLQ or DLX, and distinguish broker from business failures in logs and metrics.
For dependency declarations, topology, conversion, publisher, listener, container, retry, or delivery setup, read [Rabbit setup and delivery](references/rabbit-setup-and-delivery.md).

## Minimal testing shape

For delivery changes, verify the affected payload, routing, and failure contracts with the existing native test setup.

```java
@SpringRabbitTest
class OrderMessagingTests {
}
```

- Verify published messages reach the expected exchange, routing key, and queue.
- Verify the listener receives the deserialized payload shape the publisher sends.
- Verify retry and dead-letter behavior on one representative failure path.
- Verify converter configuration matches the payload format used in production.

Open [references/testing-support-and-listener-harnesses.md](references/testing-support-and-listener-harnesses.md) when the task needs listener-test harnesses, broker-backed integration depth, or repeatable contract checks.

## Failure classification

- Treat message conversion, payload validation, and listener-signature mismatches as contract failures.
- Treat broker disconnects, transient downstream outages, and resource exhaustion as infrastructure failures that may justify retry.
- Treat exhausted retries, rejected business operations, and poison messages as dead-letter or recovery-path failures that must stay observable.

## Configuration shapes

For naming, publishing, listeners, container factories, or Boot customization hooks, read [output and configuration shapes](references/output-and-configuration-shapes.md).

## Production guardrails

Check the changed routing or delivery guarantee using the actual exchange, binding, and queue names the application declares.

- Keep topology names stable after publishers and consumers are deployed.
- Make listeners idempotent because duplicate delivery and redelivery can occur.
- Bound concurrency only after checking ordering requirements and downstream capacity.
- Keep dead-letter queues observable and drainable.
- Make broker credentials, virtual hosts, and listener tuning explicit configuration, not hidden defaults.
