---
name: "spring-statemachine"
description: "Use this skill when modeling explicit application lifecycles with Spring Statemachine, including states, events, guards, actions, extended state, persistence, and state-machine tests."
metadata:
  title: "Spring Statemachine"
  official_project_url: "https://spring.io/projects/spring-statemachine"
  reference_doc_urls:
    - "https://docs.spring.io/spring-statemachine/docs/current/reference/"
  version: "4.0.1"
---

Use this skill when modeling explicit application lifecycles with Spring Statemachine, including states, events, guards, actions, extended state, persistence boundaries, and state-machine tests.

The current stable Spring Statemachine line is 4.0.1. The official project README marks the project as maintenance mode, so prefer the ordinary, well-supported configuration path unless the workflow clearly needs factories, persistence, pseudo states, or reactive dispatch.

## Boundaries

Use `spring-statemachine` for finite-state lifecycle modeling where legal transitions matter and invalid event handling must be explicit.

- Keep event transport and messaging infrastructure outside this skill's scope.
- Keep business rules in guards and actions only when they are part of transition semantics. Broader domain logic should stay in application services.
- Keep pseudo states, regions, persistence, and factories out of the ordinary path unless the workflow truly needs them.
- Keep reactive state-machine support out of the ordinary path. Open the reactive reference only when the application actually uses reactive guards, actions, or event handling.
- Keep distributed coordination out of the ordinary path. Persistence does not imply a distributed machine, and a distributed machine is not the default solution for restart survival.

## Common path

The ordinary Spring Statemachine job is:

1. Define the states and events as enums before writing configuration.
2. Model only the valid external transitions and let unsupported events remain explicit failures or no-ops.
3. Use guards for transition eligibility and actions for side effects tied to a transition.
4. Keep transient workflow context in extended state only when it truly belongs to the state machine.
5. Send events through the machine and observe state changes with a listener or test plan.
6. Add a test that proves the expected event sequence reaches the right terminal or intermediate state.

### Branch selector

| Situation | Stay here or open a reference |
| --- | --- |
| One singleton machine with external transitions, guards, actions, listeners, and in-memory tests | Stay in `SKILL.md` |
| Many machine instances, persisted state, regions, or persistence-aware tests | Open [references/when-single-machine-lifecycle-is-not-enough.md](references/when-single-machine-lifecycle-is-not-enough.md) |
| Choice, junction, fork, join, or history semantics | Open [references/pseudo-states.md](references/pseudo-states.md) |
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

Use the BOM once and keep the Statemachine modules versionless underneath it. When Spring Boot already manages the same Statemachine line, keep the child artifacts versionless there too and only pin the BOM or module version when the example is intentionally overriding the managed line.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.statemachine</groupId>
            <artifactId>spring-statemachine-bom</artifactId>
            <version>4.0.1</version>
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

```bash
./mvnw test -Dtest=OrderStateMachineTests
```

```bash
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

Start with one machine and one clear lifecycle. Add factories, persistence, pseudo states, or regions only when the workflow truly requires them.

## Coding procedure

1. Keep state and event names business-meaningful and stable.
2. Use external transitions for ordinary lifecycle movement and reserve pseudo states for genuinely branching workflows.
3. Put eligibility checks in guards instead of burying them in actions.
4. Keep actions idempotent when retries or duplicate events are possible.
5. Keep extended state small and explicit so guards and actions can reason about it safely.
6. Persist state only when the lifecycle must survive process restarts or multiple runtime instances.
7. Test the happy path and at least one blocked or invalid transition path.

## Edge cases

- Open [references/when-single-machine-lifecycle-is-not-enough.md](references/when-single-machine-lifecycle-is-not-enough.md) when one singleton machine must become many machine instances, persistence is enabled, or region modeling enters the design.
- Open [references/pseudo-states.md](references/pseudo-states.md) when branching semantics go beyond guarded external transitions.
- Open [references/reactive-support.md](references/reactive-support.md) when actions, guards, or event dispatch must stay reactive end to end.

## Implementation examples

### State machine configuration

```java
enum States { NEW, PAID, SHIPPED, CANCELLED }
enum Events { PAY, SHIP, CANCEL }

@Configuration
@EnableStateMachine
class OrderStateMachineConfig extends StateMachineConfigurerAdapter<States, Events> {
    @Override
    public void configure(StateMachineStateConfigurer<States, Events> states) throws Exception {
        states.withStates()
            .initial(States.NEW)
            .state(States.PAID)
            .end(States.SHIPPED)
            .end(States.CANCELLED);
    }

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
        @Override
        public void stateChanged(State<States, Events> from, State<States, Events> to) {
            auditService.record(from == null ? null : from.getId(), to == null ? null : to.getId());
        }
    };
}

@Override
public void configure(StateMachineConfigurationConfigurer<States, Events> config) throws Exception {
    config.withConfiguration().listener(stateChangeListener());
}
```

### Event dispatch shape

```java
boolean accepted = stateMachine.sendEvent(Events.PAY);
```

### JUnit 5 test shape

```java
@SpringBootTest
class OrderStateMachineTests {
    @Autowired
    StateMachine<States, Events> stateMachine;

    @Test
    void payTransitionReachesPaidState() {
        stateMachine.start();
        boolean accepted = stateMachine.sendEvent(Events.PAY);
        assertAll(
            () -> assertTrue(accepted),
            () -> assertEquals(States.PAID, stateMachine.getState().getId())
        );
    }

    @Test
    void invalidEventLeavesStateUnchanged() {
        stateMachine.start();
        stateMachine.sendEvent(Events.SHIP);
        assertEquals(States.NEW, stateMachine.getState().getId());
    }
}
```

## Output contract

Return:

1. The named states and events that define the lifecycle
2. The dependency shape, including the BOM import and the versionless modules required for runtime and tests
3. The transition shape, including where guards and actions apply
4. Any extended-state variables that are required for transition decisions
5. The event-dispatch and listener shape used to observe lifecycle movement
6. The JUnit 5 test shape proving the happy path and at least one blocked or invalid transition path
7. Any blocker that requires factories, persistence, pseudo states, regions, or reactive support

## Testing checklist

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
- Treat state-machine tests as part of the lifecycle compatibility surface.

## References

- Open [references/when-single-machine-lifecycle-is-not-enough.md](references/when-single-machine-lifecycle-is-not-enough.md) when the ordinary single-machine lifecycle is not enough and the task needs factories, persistence, regions, or deeper testing patterns.
- Open [references/pseudo-states.md](references/pseudo-states.md) when branching needs explicit choice, junction, fork, join, or history modeling.
- Open [references/reactive-support.md](references/reactive-support.md) when the machine must stay reactive.
