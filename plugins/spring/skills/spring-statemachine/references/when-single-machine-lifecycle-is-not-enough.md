# Spring Statemachine when one machine lifecycle is not enough

Open this reference when the ordinary single-machine lifecycle in `SKILL.md` is not enough and the task needs many machine instances, persisted state, regions, or more advanced testing.

## Factory usage

Use `@EnableStateMachineFactory` when the application needs many machine instances with the same topology rather than one singleton machine.

```java
@Configuration
@EnableStateMachineFactory
class OrderStateMachineFactoryConfig extends EnumStateMachineConfigurerAdapter<States, Events> {
    @Override
    public void configure(StateMachineStateConfigurer<States, Events> states) throws Exception {
        states.withStates().initial(States.NEW).state(States.PAID);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<States, Events> transitions) throws Exception {
        transitions.withExternal().source(States.NEW).target(States.PAID).event(Events.PAY);
    }
}
```

Prefer one topology with many instances over many slightly different machine definitions.

```java
@Service
class OrderWorkflowService {
    private final StateMachineFactory<States, Events> factory;
    OrderWorkflowService(StateMachineFactory<States, Events> factory) {
        this.factory = factory;
    }
    StateMachine<States, Events> createMachine(String machineId) {
        return factory.getStateMachine(machineId);
    }
}
```

Factory-created machines must be started explicitly:

```java
StateMachine<States, Events> machine = factory.getStateMachine("order-1");
machine.startReactively().block();
```

## Machine ID

Use `machineId` to identify machines within a factory.
The ID maps to `StateMachine.getId()` and is used as the context key for persistence.

```java
config.withConfiguration().machineId("myMachine");
```

With `@EnableStateMachine`, set the machine ID through the annotation's `name` field.
With a builder, set it through `StateMachineBuilder.configureConfiguration().withConfiguration().machineId(...)`.

## Persistence boundary

Persist machine state only when the lifecycle must survive restarts or be resumed later.

- Good fit: long-running orders, approvals, fulfillment, or manual review flows.
- Poor fit: short in-memory UI flows that already live inside one request sequence.
- Persistence keeps one machine restorable across time.
  - It does not by itself create distributed coordination across nodes.

### StateMachinePersister

Use `StateMachinePersister` when save and restore happen around an explicit application boundary.
The default implementation is `DefaultStateMachinePersister`, backed by a `StateMachinePersist` implementation.

Implement `StateMachinePersist<S, E, T>` to define where state is stored:

```java
class InMemoryStateMachinePersist<S, E, T> implements StateMachinePersist<S, E, T> {
    private final Map<T, StateMachineContext<S, E>> contexts = new HashMap<>();
    @Override
    public void write(StateMachineContext<S, E> context, T contextObj) throws Exception {
        contexts.put(contextObj, context);
    }
    @Override
    public StateMachineContext<S, E> read(T contextObj) throws Exception {
        return contexts.get(contextObj);
    }
}
```

Register the persister bean:

```java
@Bean
StateMachinePersister<States, Events, String> stateMachinePersister(StateMachinePersist<States, Events, String> persist) {
    return new DefaultStateMachinePersister<>(persist);
}
```

### StateMachineRuntimePersister

Use `StateMachineRuntimePersister` when persistence must stay attached to runtime transitions rather than a separate save or restore step.
It extends `StateMachinePersist` and provides a `StateMachineInterceptor` that persists state changes automatically during transitions.

Built-in implementations exist for JPA (`JpaPersistingStateMachineInterceptor`), MongoDB (`MongoDbPersistingStateMachineInterceptor`), and Redis (`RedisPersistingStateMachineInterceptor`).

Wire a runtime persister through configuration:

```java
@Override
public void configure(StateMachineConfigurationConfigurer<States, Events> config) throws Exception {
    config.withPersistence().runtimePersister(stateMachineRuntimePersister);
}
```

### Persistence with Redis

`RedisStateMachineContextRepository` provides a Redis-backed `StateMachinePersist` using Kryo serialization.
It requires a `RedisConnectionFactory`.

## Region usage

Use regions only when a workflow genuinely has parallel independent sub-lifecycles.

Regions are created when a hierarchical state has multiple sub-state groups, each with its own initial state.
The framework detects multiple initial states under the same parent and creates orthogonal regions automatically.

Use `region(String id)` to assign stable region IDs for reliable persistence:

```java
states.withStates()
    .initial(States.S1)
    .state(States.S2)
    .and()
    .withStates()
        .parent(States.S2)
        .region("R1")
        .initial(States.R1_INITIAL)
        .and()
    .withStates()
        .parent(States.S2)
        .region("R2")
        .initial(States.R2_INITIAL);
```

If the lifecycle is conceptually linear, regions usually add complexity without value.

## StateMachineInterceptor

Use `StateMachineInterceptor` when you need to intercept and potentially alter or block state changes.
It is a deeper hook than `StateMachineListener` because interceptors can prevent transitions from completing.

Register through `StateMachineAccessor`:

```java
stateMachine.getStateMachineAccessor()
    .doWithRegion(access -> access.addStateMachineInterceptor(new StateMachineInterceptorAdapter<>() {
        @Override
        public StateContext<States, Events> preTransition(StateContext<States, Events> context) {
            return context;
        }
        @Override
        public Exception stateMachineError(StateMachine<States, Events> machine, Exception exception) {
            return exception;
        }
    }));
```

## StateMachineService

`StateMachineService` provides higher-level acquire and release semantics for factory-created machines.
The default implementation is `DefaultStateMachineService`.

## Testing persisted machines

Test the save and restore boundary separately from the transition rules.

```java
@SpringBootTest
class OrderPersistenceTests {
    @Autowired
    StateMachineFactory<States, Events> factory;
    @Autowired
    StateMachinePersister<States, Events, String> stateMachinePersister;

    @Test
    void persistedMachineRestoresPaidState() throws Exception {
        StateMachine<States, Events> machine = factory.getStateMachine("order-1");
        machine.startReactively().block();
        machine.sendEvent(Mono.just(MessageBuilder.withPayload(Events.PAY).build())).blockLast();
        stateMachinePersister.persist(machine, "order-1");
        StateMachine<States, Events> restored = factory.getStateMachine("order-1");
        stateMachinePersister.restore(restored, "order-1");
        assertAll(() -> assertEquals(States.PAID, restored.getState().getId()), () -> assertEquals("order-1", restored.getId()));
    }
}
```

Verify both the restored state and any required extended-state variables.

Persisted-machine testing belongs here, not in the ordinary-path checklist, because save and restore behavior matters only after persistence is enabled.

## Decision points

| Situation | Use |
| --- | --- |
| One singleton machine per application | Stay with `@EnableStateMachine` in `SKILL.md` |
| Many runtime instances share one topology | `@EnableStateMachineFactory` |
| A machine must survive restart or resume later | persistence support |
| The workflow has parallel independent sub-lifecycles | regions |
| Persist state changes automatically during transitions | `StateMachineRuntimePersister` |
| Intercept and potentially block transitions | `StateMachineInterceptor` |
| Acquire and release factory machines | `StateMachineService` |

## Verification rule

Verify that the chosen factory or persistence path solves a real lifecycle requirement, then prove it with a save or restore test instead of assuming the machine state survives process boundaries.

## Related blockers

- Open [`pseudo-states.md`](pseudo-states.md) when the branching model depends on choice, junction, fork, join, or history semantics.
- Open [`reactive-support.md`](reactive-support.md) when the machine's actions, guards, or event flow must stay reactive.
