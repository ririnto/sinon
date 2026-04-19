# Spring Modulith scenario tests

Open this reference when event-driven module tests need richer `Scenario` verification than the ordinary module test path.

## Scenario test shape

```java
@ApplicationModuleTest
class OrdersScenarioTest {
    @Test
    void publishesOrderCompleted(Scenario scenario) {
        scenario.publish(new OrderCompleted("o-1"));
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

## Decision points

| Situation | Use |
| --- | --- |
| Event-driven interaction needs richer verification | scenario test |
| Ordinary verification and module test are enough | stay on the common path |
