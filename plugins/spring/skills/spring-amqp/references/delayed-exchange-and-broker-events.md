# Delayed exchange and broker events

Open this reference when the common-path topology in [SKILL.md](../SKILL.md) is not enough and the blocker is delayed delivery semantics or broker-side operational events.

## Delayed exchange blocker

**Problem:** the business flow needs delayed delivery rather than immediate routing or ad hoc sleep-based retry logic.

**Solution:** use an explicit delayed exchange instead of burying delay behavior in listener code.

```java
@Bean
CustomExchange delayedExchange() {
    return new CustomExchange("orders.delayed", "x-delayed-message", true, false, Map.of("x-delayed-type", "direct"));
}
```

## Broker events blocker

**Problem:** the application must observe broker-side availability or topology-related signals.

**Solution:** wire broker event handling explicitly and keep those listeners operational, not business-facing.

```java
@EventListener
void handleDeclarationException(DeclarationExceptionEvent event) {
    log.atInfo().log(() -> "topology declaration failed for " + event.getDeclarable());
}
```

## Pitfalls

- Do not hide delayed delivery inside application sleeps or retry loops.
- Do not mix operational broker events with business message listeners.
