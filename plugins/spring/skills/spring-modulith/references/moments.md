# Moments

Open this reference when the application reacts to business-relevant time events such as day, week, or month boundaries.

## Moments boundary

Use Moments for domain-relevant time events, not for generic infrastructure cron jobs. Use scheduled tasks for infrastructure-level periodic work that has no domain meaning.

## Starter shape

```xml
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-moments</artifactId>
</dependency>
```

## Configuration shape

```properties
spring.modulith.moments.enable-time-machine=true
spring.modulith.moments.zone-id=UTC
spring.modulith.moments.granularity=HOURS
spring.modulith.moments.locale=en-US
```

| Property | Default | Description |
| --- | --- | --- |
| `enable-time-machine` | `false` | Enables the `TimeMachine` bean for testing. |
| `zone-id` | `UTC` | Timezone for published time events. |
| `granularity` | `HOURS` | Granularity of events: `HOURS` or `DAYS`. |
| `locale` | JVM default | Locale used when determining week boundaries. |

## Listener shape

```java
@Component
class BillingCycleListener {

    @ApplicationModuleListener
    void on(DayHasPassed event) {
    }
}
```

Both `@EventListener` and `@ApplicationModuleListener` work for Moments events. Use `@ApplicationModuleListener` when the listener belongs to the cross-module boundary.

## Time machine test shape

```java
@ApplicationModuleTest
class BillingCycleTest {

    @Autowired
    TimeMachine timeMachine;

    @Autowired
    BillingCycleListener listener;

    @ExtendWith(PublishedEventsExtension.class)
    @Test
    void dayPassesTriggersBilling(PublishedEvents events) {
        timeMachine.shiftBy(Duration.ofDays(1));
        assertThat(events.ofType(DayHasPassed.class)).hasSize(1);
        timeMachine.reset();
    }
}
```

Use `timeMachine.reset()` to clear accumulated time shifts between tests when the `TimeMachine` bean is shared.

## Jackson serialization

Moments events support Jackson serialization for JSON-based event stores. Include the `spring-modulith-moments` dependency and configure Jackson to handle `Duration`-based time event payloads as needed for externalized or persisted moments.

## Decision points

| Situation | Use |
| --- | --- |
| Domain reacts to calendar boundaries | Moments |
| Generic scheduled infrastructure tasks | `@Scheduled` or Spring scheduler |

## Verification rule

Verify one time-machine-driven test proves a business-time event fires in the configured zone before wiring production listeners to it.
