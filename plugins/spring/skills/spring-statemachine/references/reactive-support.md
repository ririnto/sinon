# Spring Statemachine reactive support

Open this reference when the application uses reactive guards, reactive actions, or reactive event flows and the machine must stay reactive end to end.

Keep the ordinary path imperative unless the surrounding application is already reactive.

Reactive support is available in the current 4.0.2 line, but it remains an additive path rather than the baseline modeling shape for this skill.

## Reactive boundary

- Use reactive guards and actions only when upstream and downstream collaborators are already reactive.
- Keep one consistent style inside a machine. Do not mix blocking side effects into an otherwise reactive machine path.

## Reactive guard and action interfaces

The framework internally wraps both `Action` and `Guard` in Reactor types. The `Action` interface (`void execute(StateContext)`) is wrapped as `ReactiveAction` (`Function<StateContext, Mono<Void>>`). The `Guard` interface (`boolean evaluate(StateContext)`) is wrapped as `ReactiveGuard` (`Function<StateContext, Mono<Boolean>>`).

You can implement these directly for fully non-blocking behavior:

```java
@Bean
ReactiveAction<States, Events> reactiveReserveInventory() {
    return context -> inventoryService.reserveReactive(context.getMessageHeaders().get("orderId", Long.class))
        .then();
}

@Bean
ReactiveGuard<States, Events> reactivePaymentAllowed() {
    return context -> Mono.just(Boolean.TRUE.equals(context.getExtendedState().get("paymentAllowed", Boolean.class)));
}
```

Reactive support changes how side effects and eligibility checks are composed, but it does not change the core modeling rule: states, events, transitions, and lifecycle semantics still need to stay explicit.

## Reactive event dispatch

Use the reactive event path when event dispatch itself must stay reactive. Unlike the ordinary imperative `sendEvent(Mono<Message>)` shape in `SKILL.md`, sending a `Flux` of messages returns a combined `Flux<StateMachineEventResult>`.

```java
Flux<StateMachineEventResult<States, Events>> results = stateMachine.sendEvents(
    Flux.just(
        MessageBuilder.withPayload(Events.PAY).build(),
        MessageBuilder.withPayload(Events.SHIP).build()
    )
);
```

The reactive `sendEvent(Mono<Message>)` and `sendEvents(Flux<Message>)` are the only dispatch APIs. The older synchronous `sendEvent(E event)` returning `boolean` is no longer present in 4.0.x.

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
    Flux<StateMachineEventResult<States, Events>> results = stateMachine.sendEvents(
        Flux.just(MessageBuilder.withPayload(Events.PAY).build())
    );
    StepVerifier.create(results)
        .expectNextCount(1)
        .verifyComplete();
    assertEquals(States.PAID, stateMachine.getState().getId());
}
```

## Gotchas

- Do not adopt reactive machine APIs just because the project uses Reactor elsewhere.
- Do not hide blocking calls inside reactive guards or actions.
- Do not treat reactive support as a substitute for persistence, regions, or pseudo states.

## Verification rule

Verify that the reactive machine path completes without blocking and reaches the intended state, then keep persistence, regions, and pseudo-state decisions separate from the reactive dispatch choice.
