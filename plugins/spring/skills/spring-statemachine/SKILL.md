---
metadata:
  reference:
    Spring Statemachine:
      version: 4.0.2
      url:
        - https://docs.spring.io/spring-statemachine/docs/current/reference/
        - https://repo.maven.apache.org/maven2/org/springframework/statemachine/spring-statemachine-bom/maven-metadata.xml
        - https://repo.maven.apache.org/maven2/org/springframework/statemachine/spring-statemachine-bom/4.0.2/spring-statemachine-bom-4.0.2.pom
name: spring-statemachine
description: >-
  Use Spring Statemachine for states, events, guards, actions, factories, persistence, and tests.
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

For dependency baseline, see [example 1](references/configuration-examples.md#example-1-dependency-baseline).

Use the starter for ordinary Boot-based state machine work and the test module for state-machine tests.

For dependency baseline, see [example 2](references/configuration-examples.md#example-2-dependency-baseline).

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

## State machine patterns

For dispatch, startup, transition types, state actions, timers, deferred events, auto-start, context events, SpEL guards, or error actions, read [state machine patterns](references/state-machine-patterns.md).
In 4.0.x `sendEvent` returns a Flux and does nothing until subscribed; use `startReactively()` for startup.
Choose external, internal, or local transitions for their distinct entry and exit semantics.

## Implementation examples

For configuration, guards, actions, extended state, listeners, dispatch, or tests, read [implementation examples](references/implementation-examples.md).
For dependency and dispatch examples, read [configuration examples](references/configuration-examples.md).

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
