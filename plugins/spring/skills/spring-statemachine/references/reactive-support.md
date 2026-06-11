# Spring Statemachine reactive support

Open this reference when application guards, actions, or event flows must stay reactive end to end. Keep the ordinary path imperative unless the surrounding application is already reactive.

Reactive support is available in the current 4.0.2 line and remains an additive path rather than the baseline modeling shape.

## Reactive boundary

- Use reactive guards and actions only when upstream and downstream collaborators are already reactive.
- Keep one consistent style inside a machine.
- Do not mix blocking side effects into a reactive machine path.

## Reactive guard and action interfaces

The framework wraps both `Action` and `Guard` into Reactor types. `Action` (`void execute(StateContext)`) can be represented as `ReactiveAction` (`Function<StateContext, Mono<Void>>`). `Guard` (`boolean evaluate(StateContext)`) can be represented as `ReactiveGuard` (`Function<StateContext, Mono<Boolean>>`).

```java
@Bean
ReactiveAction<States, Events> reactiveReserveInventory() {
    return context -> inventoryService.reserveReactive(context.getMessageHeaders().get("orderId", Long.class)).then();
}

@Bean
ReactiveGuard<States, Events> reactivePaymentAllowed() {
    return context -> Mono.just(Boolean.TRUE.equals(context.getExtendedState().get("paymentAllowed", Boolean.class)));
}
```

Reactive support changes how side effects and eligibility checks are composed, but it does not change the core modeling rule: states, events, transitions, and lifecycle semantics still need to stay explicit.

## Reactive event dispatch

Use the reactive event path when event dispatch itself must stay reactive. The reactive `sendEvent(Mono<Message>)` and `sendEvents(Flux<Message>)` APIs require subscription, and `sendEvents(Flux<Message>)` returns a combined `Flux<StateMachineEventResult>`. The synchronous boolean `sendEvent(...)` methods are still present in the current 4.0.2 line.

```java
Flux<StateMachineEventResult<States, Events>> results = stateMachine.sendEvents(Flux.just(MessageBuilder.withPayload(Events.PAY).build(), MessageBuilder.withPayload(Events.SHIP).build()));
```

## Reactive machine startup

```java
StepVerifier.create(stateMachine.startReactively()).verifyComplete();
```

## Testing rule

Test reactive machines with assertions that prove the event flow completes and reaches the expected state without hidden blocking behavior.

```java
@Test
void reactivePayEventReachesPaidState() {
    StepVerifier.create(stateMachine.startReactively()).verifyComplete();
    Flux<StateMachineEventResult<States, Events>> results = stateMachine.sendEvents(Flux.just(MessageBuilder.withPayload(Events.PAY).build()));
    StepVerifier.create(results).expectNextCount(1).verifyComplete();
    assertEquals(States.PAID, stateMachine.getState().getId());
}
```
