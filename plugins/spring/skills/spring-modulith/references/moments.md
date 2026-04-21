# Spring Modulith Moments

Open this reference when the application reacts to business-relevant time events such as day, week, or month boundaries.

## Moments boundary

Use Moments when the application reacts to domain-relevant time events, not for generic infrastructure cron jobs.

## Moments starter shape

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-moments</artifactId>
</dependency>
```

## Time-machine configuration shape

```properties
spring.modulith.moments.enable-time-machine=true
spring.modulith.moments.zone-id=UTC
```

## Listener shape

```java
@Component
class BillingCycleListener {
    @EventListener
    void on(DayHasPassed event) {
    }
}
```

Use plain `@EventListener` here because Moments emits application events from the time abstraction itself; this is not the same cross-module boundary contract as `@ApplicationModuleListener` for ordinary module collaboration.

## Verification shape

```java
@ExtendWith(PublishedEventsExtension.class)
@SpringBootTest(properties = "spring.modulith.moments.enable-time-machine=true")
class BillingCycleMomentsTest {
    @Autowired
    TimeMachine timeMachine;

    @Test
    void emitsDayHasPassed(PublishedEvents events) {
        timeMachine.shiftBy(Duration.ofDays(1));
        assertThat(events.ofType(DayHasPassed.class)).hasSize(1);
    }
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Domain reacts to calendar boundaries | Moments |
| Generic scheduled infrastructure work | other scheduling support |

## Verification rule

Verify one time-machine-driven test proves the expected business-time event fires in the configured zone before wiring production listeners to it.
