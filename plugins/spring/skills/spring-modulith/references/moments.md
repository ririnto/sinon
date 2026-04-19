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

## Decision points

| Situation | Use |
| --- | --- |
| Domain reacts to calendar boundaries | Moments |
| Generic scheduled infrastructure work | other scheduling support |
