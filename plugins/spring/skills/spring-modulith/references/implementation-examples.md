# Modulith implementation examples

## Boundary verification test

```java
class ModularityTests {
    @Test
    void verifiesModuleBoundaries() {
        ApplicationModules.of(Application.class).verify();
    }
}
```

## Module event publication

```java
@Service
class Orders {
    private final ApplicationEventPublisher events;

    Orders(ApplicationEventPublisher events) {
        this.events = events;
    }

    void complete(Order order) {
        order.complete();
        events.publishEvent(new OrderCompleted(order.id()));
    }
}
```

## Event listener in another module

```java
@Component
class InventoryProjection {
    private final InventoryReadModel readModel;

    InventoryProjection(InventoryReadModel readModel) {
        this.readModel = readModel;
    }

    @ApplicationModuleListener
    void on(OrderCompleted event) {
        readModel.markCompleted(event.orderId());
    }
}
```

## Module test shape

```java
@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.STANDALONE)
class OrdersModuleTest {
    @Autowired
    Orders orders;

    @Test
    void completesOrderAndPublishesModuleEvent(PublishedEvents publishedEvents) {
        orders.complete(new Order("o-1"));
        assertThat(publishedEvents.ofType(OrderCompleted.class)).hasSize(1);
    }
}
```

## Assertable published events test shape

```java
@ApplicationModuleTest
class OrdersModuleTest {
    @Autowired
    Orders orders;

    @Test
    void publishesOrderCompleted(AssertablePublishedEvents events) {
        orders.complete(new Order("o-1"));
        assertThat(events)
            .contains(OrderCompleted.class)
            .matching(OrderCompleted::orderId, "o-1");
    }
}
```

## Named interface shape

```text
example.orders.api
example.orders.internal
```

```java
@ApplicationModule(allowedDependencies = "inventory::api")
package example.orders;
```

## Documentation generation shape

The `Documenter` test requires `spring-modulith-docs` in test scope:

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-docs</artifactId>
    <scope>test</scope>
</dependency>
```

```java
class DocumentationTests {
    ApplicationModules modules = ApplicationModules.of(Application.class);

    @Test
    void writeDocumentationSnippets() {
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml()
            .writeModuleCanvases()
            .writeAggregatingDocument();
    }
}
```

Generate C4 component diagrams, per-module diagrams, module canvases, and an aggregating Asciidoctor file.
Output goes to `spring-modulith-docs` in the build folder by default.
