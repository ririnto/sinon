# State machine patterns

## Event dispatch

In 4.0.x, `sendEvent` accepts `Mono<Message<E>>` and returns `Flux<StateMachineEventResult<S, E>>`.
Nothing happens until the returned Flux is subscribed.
Build events with `MessageBuilder`.

```java
Message<Events> message = MessageBuilder
    .withPayload(Events.PAY)
    .setHeader("orderId", 42L)
    .build();
stateMachine.sendEvent(Mono.just(message)).subscribe();
```

For synchronous-style usage, block on the result:

For event dispatch, see [example 3](configuration-examples.md#example-3-event-dispatch).

## Machine startup

Use `startReactively()` instead of `start()`:

```java
stateMachine.startReactively().block();
```

## Transition types

Three transition types are supported:

- `withExternal()` - source and target differ, so entry and exit actions run.
- `withInternal()` - source and target are the same, so no entry or exit actions run.
- `withLocal()` - source and target are at the same state hierarchy level, so no exit or entry actions run for sub-states.

## State entry, exit, and do actions

```java
states.withStates()
    .initial(States.NEW)
    .state(States.PAID, onEnterAction(), onExitAction())
    .end(States.SHIPPED);
```

## Timer triggers

Use `timer(ms)` for repeating internal transitions and `timerOnce(ms)` for one-shot delayed transitions on internal transitions.

```java
transitions.withInternal()
    .source(States.PAID)
    .action(timeoutAction())
    .timer(5000);
```

## Deferred events

States can defer events for later processing.
When the machine enters a state that does not defer the event, previously deferred events are replayed.

```java
states.withStates()
    .initial(States.READY)
    .state(States.READY, null, null, EnumSet.of(Events.DEPLOY));
```

## Auto-start

Set `autoStartup(true)` in configuration to avoid calling `startReactively()` manually.

For auto-start, see [example 4](configuration-examples.md#example-4-auto-start).

## Disabling context events

When `@EnableStateMachine` context events are not needed, disable them to reduce overhead:

```java
@Configuration
@EnableStateMachine(contextEvents = false)
class OrderStateMachineConfig {
}
```

## SpEL guards

Use `guardExpression` for simple eligibility checks without a dedicated Guard bean:

```java
transitions.withExternal()
    .source(States.NEW).target(States.PAID).event(Events.PAY)
    .guardExpression("extendedState.variables.get('paymentAllowed')");
```

## Error action callback

Provide an error action to handle exceptions thrown by the primary action:

```java
transitions.withExternal()
    .source(States.NEW).target(States.PAID).event(Events.PAY)
    .action(reserveInventory(), handleError());
```
