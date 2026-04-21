# Spring Data domain events

Open this reference when the blocker is aggregate-root domain event publication through Spring Data repositories.

## Aggregate-root event blocker

Use repository-driven domain events only when the aggregate itself owns the event lifecycle.

```java
class Order extends AbstractAggregateRoot<Order> {
    void markSubmitted() {
        registerEvent(new OrderSubmitted(id));
    }
}
```

Keep event registration inside aggregate behavior so the repository save path publishes events that reflect real state changes.

## Repository save verification shape

```java
@Test
void publishesOrderSubmittedEvent() {
    order.markSubmitted();
    repository.save(order);
    assertThat(events.stream(OrderSubmitted.class).count()).isEqualTo(1);
}
```

## Publication lifecycle blocker

Use `@DomainEvents` and `@AfterDomainEventPublication` when the aggregate does not extend `AbstractAggregateRoot`.

```java
@DomainEvents
Collection<Object> domainEvents() {
    return this.events;
}

@AfterDomainEventPublication
void clearDomainEvents() {
    this.events.clear();
}
```

Remember that `deleteById(...)` does not publish aggregate-root events because no aggregate instance is loaded for that path.

## Decision points

| Situation | First check |
| --- | --- |
| Aggregate behavior should emit events on save | use aggregate-root event registration |
| Event list keeps republishing on later saves | verify `@AfterDomainEventPublication` cleanup |
| Delete path must publish a domain event | do not rely on `deleteById(...)` |
| Event handling depends on one store's semantics | move to the store-specific path |

## Verification rule

Verify one save path publishes exactly one event and one later save does not republish stale events after cleanup.
