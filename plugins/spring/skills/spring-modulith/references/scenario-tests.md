# Spring Modulith scenario tests

Open this reference when event-driven module tests need richer `Scenario` verification than the ordinary module test path.

## Scenario test shape

```java
@ApplicationModuleTest
class OrdersScenarioTest {
    @Autowired
    Orders orders;

    @Test
    void publishesOrderCompleted(Scenario scenario) {
        scenario.stimulate(() -> orders.complete(new Order("o-1")))
            .andWaitForEventOfType(OrderCompleted.class)
            .toArrive();
    }
}
```

Use one scenario test per interaction path so failures point to one boundary problem.

## Scenario verification shape

```java
@ApplicationModuleTest
class OrdersScenarioTest {
    @Test
    void publishesOrderCompleted(Scenario scenario) {
        scenario.publish(new OrderCompleted("o-1"))
            .andWaitForEventOfType(InventoryUpdated.class)
            .toArrive();
    }
}
```

## Failure-path verification shape

```java
@ApplicationModuleTest
class OrdersScenarioTest {
    @Autowired
    Orders orders;

    @Test
    void publishesOrderRejected(Scenario scenario) {
        scenario.stimulate(() -> orders.reject("o-1"))
            .andWaitAtMost(Duration.ofSeconds(2))
            .andWaitForEventOfType(OrderRejected.class)
            .toArrive();
    }
}
```

Use one success path and one explicit alternative event path so the scenario test proves both the intended reaction and a concrete failure or rejection branch.

## Decision points

| Situation | Use |
| --- | --- |
| Event-driven interaction needs richer verification | scenario test |
| Ordinary verification and module test are enough | stay on the common path |

## Verification rule

Verify one scenario test uses an explicit timeout and one assertion on the expected published event or side effect so failures do not hang indefinitely.
