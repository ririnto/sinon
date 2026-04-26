# Spring Statemachine reactive support

Open this reference when the application uses reactive guards, reactive actions, or reactive event flows and the machine must stay reactive end to end.

Keep the ordinary path imperative unless the surrounding application is already reactive.

Reactive support is available in the current 4.0.1 line, but it remains an additive path rather than the baseline modeling shape for this skill.

## Reactive boundary

- Use reactive guards and actions only when upstream and downstream collaborators are already reactive.
- Keep one consistent style inside a machine. Do not mix blocking side effects into an otherwise reactive machine path.

## Reactive action and guard rule

Reactive support changes how side effects and eligibility checks are composed, but it does not change the core modeling rule: states, events, transitions, and lifecycle semantics still need to stay explicit.

## Reactive event dispatch shape

```java
Flux<StateMachineEventResult<States, Events>> results = stateMachine.sendEvents(Flux.just(MessageBuilder.withPayload(Events.PAY).build()));
```

Use the reactive event path when event dispatch itself must stay reactive. Unlike the ordinary imperative `sendEvent(...)` shape in `SKILL.md`, the reactive path returns event results rather than a simple boolean acceptance flag.

Keep result handling explicit so the reactive test proves both completion and the expected machine state.

## Testing rule

Test reactive machines with assertions that prove the event flow completes and reaches the expected state without hidden blocking behavior.

```java
@Test
void reactivePayEventReachesPaidState() {
    Flux<StateMachineEventResult<States, Events>> results = stateMachine.sendEvents(Flux.just(MessageBuilder.withPayload(Events.PAY).build()));
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
