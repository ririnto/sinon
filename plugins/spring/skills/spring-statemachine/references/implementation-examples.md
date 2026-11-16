# Statemachine implementation examples

## State machine configuration

```java
enum States { NEW, PAID, SHIPPED, CANCELLED }
enum Events { PAY, SHIP, CANCEL }

@Configuration
@EnableStateMachine
class OrderStateMachineConfig extends EnumStateMachineConfigurerAdapter<States, Events> {
    /**
     * Declares the machine states, including the initial and end states.
     */
    @Override
    public void configure(StateMachineStateConfigurer<States, Events> states) throws Exception {
        states.withStates()
            .initial(States.NEW)
            .state(States.PAID)
            .end(States.SHIPPED)
            .end(States.CANCELLED);
    }

    /**
     * Declares the external transitions between states and their triggering events.
     */
    @Override
    public void configure(StateMachineTransitionConfigurer<States, Events> transitions) throws Exception {
        transitions
            .withExternal().source(States.NEW).target(States.PAID).event(Events.PAY)
            .and()
            .withExternal().source(States.PAID).target(States.SHIPPED).event(Events.SHIP)
            .and()
            .withExternal().source(States.NEW).target(States.CANCELLED).event(Events.CANCEL);
    }
}
```

## Guard shape

```java
@Bean
Guard<States, Events> paymentAllowed() {
    return context -> Boolean.TRUE.equals(context.getExtendedState().get("paymentAllowed", Boolean.class));
}
```

## Action shape

```java
@Bean
Action<States, Events> reserveInventory() {
    return context -> {
        Long orderId = context.getMessageHeaders().get("orderId", Long.class);
        inventoryService.reserve(orderId);
    };
}
```

## Extended state shape

```java
stateMachine.getExtendedState().getVariables().put("paymentAllowed", true);
```

## Listener shape

```java
@Bean
StateMachineListener<States, Events> stateChangeListener() {
    return new StateMachineListenerAdapter<>() {
        /**
         * Records every state change in the audit service.
         */
        @Override
        public void stateChanged(State<States, Events> from, State<States, Events> to) {
            auditService.record(from == null ? null : from.getId(), to == null ? null : to.getId());
        }
    };
}

/**
 * Applies configuration-model options such as auto-startup and listeners.
 */
@Override
public void configure(StateMachineConfigurationConfigurer<States, Events> config) throws Exception {
    config.withConfiguration().listener(stateChangeListener());
}
```

## Event dispatch shape

```java
Message<Events> message = MessageBuilder.withPayload(Events.PAY).build();
stateMachine.sendEvent(Mono.just(message)).blockLast();
```

## JUnit 5 test shape

```java
@SpringBootTest
class OrderStateMachineTests {
    @Autowired
    StateMachine<States, Events> stateMachine;

    @Test
    void payTransitionReachesPaidState() {
        stateMachine.startReactively().block();
        boolean accepted = stateMachine
            .sendEvent(Mono.just(MessageBuilder.withPayload(Events.PAY).build()))
            .map(StateMachineEventResult::getResultType)
            .all(r -> r == StateMachineEventResult.ResultType.ACCEPTED)
            .blockOptional()
            .orElse(false);
        assertAll(() -> assertTrue(accepted), () -> assertEquals(States.PAID, stateMachine.getState().getId()));
    }

    @Test
    void invalidEventLeavesStateUnchanged() {
        stateMachine.startReactively().block();
        stateMachine.sendEvent(Mono.just(MessageBuilder.withPayload(Events.SHIP).build())).blockLast();
        assertEquals(States.NEW, stateMachine.getState().getId());
    }
}
```
