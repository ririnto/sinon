# Testing support and listener harnesses

Open this reference when the minimal delivery test in [SKILL.md](../SKILL.md) is not enough and the blocker is listener-test harness depth, broker-backed integration verification, or repeatable contract checks.

## Fast listener harness

**Problem:** the module needs fast feedback on listener behavior without manually driving a real broker for every check.

**Solution:** use Spring Rabbit test support and keep assertions focused on delivery contract, conversion, and delegation.

```java
@SpringRabbitTest
class OrderListenerTests {
}
```

Assert payload shape and the selected headers that the listener contract actually depends on.

## Broker-backed routing verification

**Problem:** unit-level harnesses are not enough because queue, exchange, retry, or DLQ wiring must be proven end to end.

**Solution:** run a broker-backed integration test and assert routing and failure outcomes explicitly.

```java
@SpringBootTest
class OrderMessagingIntegrationTests {
}
```

Verify the published message reaches the expected exchange, routing key, queue, and dead-letter path.

```java
assertAll(
    () -> assertEquals("orders.created", receivedRoutingKey),
    () -> assertEquals("42", event.orderId())
);
```

Use representative payload fixtures and assert conversion plus selected headers together in the same end-to-end path.

## Decision points

| Situation | First choice |
| --- | --- |
| Need fast listener behavior checks | Spring Rabbit test support |
| Need real routing and retry verification | broker-backed integration test |
| Need DLQ behavior proof | end-to-end failure-path test |

## Pitfalls

- Do not stop at plain publisher tests when the listener or DLQ path is the real contract.
- Do not rely on generic smoke tests without asserting routing keys, queue names, and payload shape.
- Do not mix business-logic assertions with messaging-contract assertions in the same test without a clear reason.
