# Scenario Tests

Open this reference when event-driven module tests need richer `Scenario` verification than the ordinary module test path.

## Scenario boundary

Use the `Scenario` API when a test must verify that an event published by one module is asynchronously consumed by another module and the outcome is observable. Use `PublishedEvents` or `AssertablePublishedEvents` for simple event-publication-only assertions.

## First safe commands

```sh
./mvnw test -Dtest=OrdersScenarioTest
```

```sh
./gradlew test --tests OrdersScenarioTest
```

## Event stimulus shape

```java
@ApplicationModuleTest
class OrdersScenarioTest {

    @Autowired
    Orders orders;

    @Test
    void orderCompletionTriggersInventoryUpdate(Scenario scenario) {
        scenario.publish(new OrderCompleted("o-1"))
            .andWaitForEventOfType(StockUpdated.class)
            .matching(event -> event.orderId().equals("o-1"))
            .toArriveAndVerify(event -> {
                assertThat(event.orderId()).isEqualTo("o-1");
            });
    }
}
```

## Bean invocation stimulus shape

```java
@Test
void completeOrderViaBean(Scenario scenario) {
    scenario.stimulate(() -> orders.complete(new Order("o-1")))
        .andWaitForEventOfType(OrderCompleted.class)
        .matching(event -> event.orderId().equals("o-1"))
        .toArriveAndVerify(event -> {
            assertThat(event.orderId()).isEqualTo("o-1");
        });
}
```

## State change verification shape

```java
@Test
void orderCompletionUpdatesReadModel(Scenario scenario) {
    scenario.publish(new OrderCompleted("o-1"))
        .andWaitForStateChange(() -> readModel.findByOrderId("o-1"))
        .andVerify(result -> {
            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(Status.COMPLETED);
        });
}
```

## Cleanup shape

Stimulus runs in a transaction that is not rolled back. Clean up state explicitly:

```java
scenario.publish(new OrderCompleted("o-1"))
    .andWaitForEventOfType(StockUpdated.class)
    .toArrive()
    .andCleanup(() -> repository.deleteById("o-1"));
```

## Timeout customization shape

```java
@Test
void withCustomTimeout(Scenario scenario) {
    scenario.publish(new OrderCompleted("o-1"))
        .customize(factory -> factory.atMost(Duration.ofSeconds(5)))
        .andWaitForEventOfType(StockUpdated.class)
        .toArrive();
}
```

## Scenario customizer shape

Customize all scenarios in a test class:

```java
@ExtendWith(MyCustomizer.class)
class OrdersScenarioTest {

    static class MyCustomizer implements ScenarioCustomizer {
        @Override
        Function<ConditionFactory, ConditionFactory> getDefaultCustomizer(
                Method method, ApplicationContext context) {
            return factory -> factory.atMost(Duration.ofSeconds(3));
        }
    }
}
```

## Scenario steps

1. Define a stimulus with `scenario.publish(event)` or `scenario.stimulate(Runnable)`.
2. Optionally customize execution with `.customize(...)` or `.waitAtMost(...)`.
3. Define the expected outcome with `.andWaitForEventOfType(...)` or `.andWaitForStateChange(...)`.
4. Optionally add `.matching(Predicate)` to constrain the expected event.
5. Complete with `.toArrive()`, `.toArriveAndVerify(Consumer)`, or `.andVerify(Consumer)`.

## Decision points

| Situation | Use |
| --- | --- |
| Verify event publication only | `PublishedEvents` or `AssertablePublishedEvents` |
| Verify async cross-module event flow | `Scenario` with `.andWaitForEventOfType(...)` |
| Verify state change after event processing | `Scenario` with `.andWaitForStateChange(...)` |
| Multiple events need verification | Multiple scenario definitions or `AssertablePublishedEvents` |

## Verification rule

Verify one scenario test has an explicit timeout and at least one assertion on the published event or resulting state so tests do not hang indefinitely.
