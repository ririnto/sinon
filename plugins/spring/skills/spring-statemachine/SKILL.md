---
metadata:
  reference:
    Spring Statemachine:
      version: 4.0.2
      url: https://docs.spring.io/spring-statemachine/docs/current/reference/
name: spring-statemachine
description: >-
  Model or troubleshoot Spring Statemachine states, events, guards, actions, factories, persistence, and lifecycle tests.
---

# Spring Statemachine

The official Spring Statemachine reference documents version 4.0.2.
The `4.0.2` BOM below illustrates the documented Statemachine pairing.
For a new BOM, check `org.springframework.statemachine:spring-statemachine-bom` on Maven Central for the latest stable release compatible with the project's Boot and Framework lines.
Keep an existing BOM or version-catalog pin unless the task authorizes changing it.
Prefer the ordinary configuration path unless the workflow clearly needs factories, persistence, pseudo states, or reactive dispatch.

Spring Statemachine 4.0.2 requires Spring Boot 3.5.x and Spring Framework 6.2.x.

## Boundaries

Use `spring-statemachine` for finite-state lifecycle modeling where legal transitions matter and invalid event handling must be explicit.

- Keep event transport and messaging infrastructure outside this skill's scope.
- Keep business rules in guards and actions only when they are part of transition semantics.
  - Broader domain logic should stay in application services.
- Keep pseudo states, regions, persistence, and factories out of the ordinary path unless the workflow truly needs them.
- Keep reactive state-machine support out of the ordinary path.
  - Open the reactive reference only when the application actually uses reactive guards, actions, or event handling.
- Keep distributed coordination out of the ordinary path.
  - Persistence does not imply a distributed machine, and a distributed machine is not the default solution for restart survival.
- Keep `@WithStateMachine` context integration, `StateMachineInterceptor`, monitoring, and security out of the ordinary path unless those features are explicitly needed.

## Task scope

Preserve state names, event names, and transition semantics unless changing their contract is in scope.
For a new lifecycle, define states and events as enums and make unsupported-event behavior explicit.
Use guards for eligibility, actions for transition side effects, and extended state only for machine-owned context.
Use the sections and references for the affected topology, dispatch, or persistence concern.

## Surface map

| Situation | Stay here or open a reference |
| --- | --- |
| One singleton machine with external transitions, guards, actions, listeners, and in-memory tests | Stay in `SKILL.md` |
| Many machine instances, persisted state, regions, or persistence-aware tests | Open [references/when-single-machine-lifecycle-is-not-enough.md](references/when-single-machine-lifecycle-is-not-enough.md) |
| Choice, junction, fork, join, history, termination, or entry/exit-point semantics | Open [references/pseudo-states.md](references/pseudo-states.md) |
| Reactive guards, actions, or event flows | Open [references/reactive-support.md](references/reactive-support.md) |

### Transition decisions

| Situation | Use |
| --- | --- |
| Transition eligibility depends on machine data | guard |
| A transition must trigger a side effect | action |
| Small workflow context must travel with the machine | extended state |
| The application must observe lifecycle movement | `StateMachineListener` |
| One topology must create many runtime instances or survive restart | factory and persistence support |

## Dependency baseline

Use the BOM once and keep the Statemachine modules versionless underneath it.
When Spring Boot already manages the same Statemachine line, keep the child artifacts versionless there too and only pin the BOM or module version when the example is intentionally overriding the managed line.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.statemachine</groupId>
            <artifactId>spring-statemachine-bom</artifactId>
            <version>4.0.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Use the starter for ordinary Boot-based state machine work and the test module for state-machine tests.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.statemachine</groupId>
        <artifactId>spring-statemachine-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.statemachine</groupId>
        <artifactId>spring-statemachine-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## First safe configuration

### First safe commands

```sh
./mvnw test -Dtest=OrderStateMachineTests
```

```sh
./gradlew test --tests OrderStateMachineTests
```

### Simple lifecycle enums

```java
enum States { NEW, PAID, SHIPPED, CANCELLED }
enum Events { PAY, SHIP, CANCEL }
```

### State machine enablement shape

```java
@Configuration
@EnableStateMachine
class OrderStateMachineConfig {
}
```

Start with one machine and one clear lifecycle.
Add factories, persistence, pseudo states, or regions only when the workflow truly requires them.

## Implementation guidance

- Keep state and event names business-meaningful and stable.
- Use external transitions for ordinary lifecycle movement and reserve pseudo states for branching or hierarchical lifecycle semantics.
- Put eligibility checks in guards instead of burying them in actions.
- Keep actions idempotent when retries or duplicate events are possible.
- Keep extended state small and explicit so guards and actions can reason about it safely.
- Persist state only when the lifecycle must survive process restarts or multiple runtime instances.

## Key API patterns

### Event dispatch

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

```java
boolean accepted = stateMachine
    .sendEvent(Mono.just(MessageBuilder.withPayload(Events.PAY).build()))
    .map(StateMachineEventResult::getResultType)
    .allMatch(r -> r == StateMachineEventResult.ResultType.ACCEPTED)
    .blockOptional()
    .orElse(false);
```

### Machine startup

Use `startReactively()` instead of `start()`:

```java
stateMachine.startReactively().block();
```

### Transition types

Three transition types are supported:

- `withExternal()` - source and target differ, so entry and exit actions run.
- `withInternal()` - source and target are the same, so no entry or exit actions run.
- `withLocal()` - source and target are at the same state hierarchy level, so no exit or entry actions run for sub-states.

### State entry, exit, and do actions

```java
states.withStates()
    .initial(States.NEW)
    .state(States.PAID, onEnterAction(), onExitAction())
    .end(States.SHIPPED);
```

### Timer triggers

Use `timer(ms)` for repeating internal transitions and `timerOnce(ms)` for one-shot delayed transitions on internal transitions.

```java
transitions.withInternal()
    .source(States.PAID)
    .action(timeoutAction())
    .timer(5000);
```

### Deferred events

States can defer events for later processing.
When the machine enters a state that does not defer the event, previously deferred events are replayed.

```java
states.withStates()
    .initial(States.READY)
    .state(States.READY, null, null, EnumSet.of(Events.DEPLOY));
```

### Auto-start

Set `autoStartup(true)` in configuration to avoid calling `startReactively()` manually.

```java
/**
 * Applies configuration-model options such as auto-startup and listeners.
 */
@Override
public void configure(StateMachineConfigurationConfigurer<States, Events> config) throws Exception {
    config.withConfiguration().autoStartup(true);
}
```

### Disabling context events

When `@EnableStateMachine` context events are not needed, disable them to reduce overhead:

```java
@Configuration
@EnableStateMachine(contextEvents = false)
class OrderStateMachineConfig {
}
```

### SpEL guards

Use `guardExpression` for simple eligibility checks without a dedicated Guard bean:

```java
transitions.withExternal()
    .source(States.NEW).target(States.PAID).event(Events.PAY)
    .guardExpression("extendedState.variables.get('paymentAllowed')");
```

### Error action callback

Provide an error action to handle exceptions thrown by the primary action:

```java
transitions.withExternal()
    .source(States.NEW).target(States.PAID).event(Events.PAY)
    .action(reserveInventory(), handleError());
```

## Implementation examples

### State machine configuration

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

### Guard shape

```java
@Bean
Guard<States, Events> paymentAllowed() {
    return context -> Boolean.TRUE.equals(context.getExtendedState().get("paymentAllowed", Boolean.class));
}
```

### Action shape

```java
@Bean
Action<States, Events> reserveInventory() {
    return context -> {
        Long orderId = context.getMessageHeaders().get("orderId", Long.class);
        inventoryService.reserve(orderId);
    };
}
```

### Extended state shape

```java
stateMachine.getExtendedState().getVariables().put("paymentAllowed", true);
```

### Listener shape

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

### Event dispatch shape

```java
Message<Events> message = MessageBuilder.withPayload(Events.PAY).build();
stateMachine.sendEvent(Mono.just(message)).blockLast();
```

### JUnit 5 test shape

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
            .allMatch(r -> r == StateMachineEventResult.ResultType.ACCEPTED)
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

## Output contract

Report the changed lifecycle contract, relevant dispatch or persistence decisions, and verification results.
Identify unverified transition behavior or blockers.
Follow the task's required response format.

## Verification

Use existing state-machine tests for changed transitions and regression risks.
Keep the JUnit 5 path for this Boot 3.5-based line.
Select checks for affected events, guards, actions, and observation behavior:

- Verify the happy-path event sequence reaches the intended state.
- Verify an invalid or disallowed event does not silently produce the wrong transition.
- Verify guards block transitions when preconditions are not met.
- Verify actions run only on the transitions that should trigger them.
- Verify listeners or assertions observe the state change that the workflow depends on.
- Use `assertAll(...)` when one test evaluates multiple assertions for the same transition outcome.

## Production checklist

- Keep state and event names stable after external systems or persisted machines depend on them.
- Make transition side effects idempotent or compensate for duplicate delivery.
- Persist state only when restart or multi-node continuity is actually required.
- Keep extended state small and serializable when the machine is persisted.
- When using Kryo-based persistence through a repository such as `RedisStateMachineContextRepository`, configure a class allowlist to prevent arbitrary class deserialization.
- Treat state-machine tests as part of the lifecycle compatibility surface.
