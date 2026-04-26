# Spring Statemachine when one machine lifecycle is not enough

Open this reference when the ordinary single-machine lifecycle in `SKILL.md` is not enough and the task needs many machine instances, persisted state, regions, or more advanced testing.

## Factory usage

Use `@EnableStateMachineFactory` when the application needs many machine instances with the same topology rather than one singleton machine.

```java
@Configuration
@EnableStateMachineFactory
class OrderStateMachineFactoryConfig {
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

## Persistence boundary

Persist machine state only when the lifecycle must survive restarts or be resumed later.

- Good fit: long-running orders, approvals, fulfillment, or manual review flows.
- Poor fit: short in-memory UI flows that already live inside one request sequence.
- Persistence keeps one machine restorable across time. It does not by itself create distributed coordination across nodes.

Use `StateMachinePersister` when save and restore can happen around an explicit application boundary. Use `StateMachineRuntimePersister` when persistence must stay attached to runtime transitions rather than a separate save or restore step.

## Region usage

Use regions only when a workflow genuinely has parallel independent sub-lifecycles.

If the lifecycle is conceptually linear, regions usually add complexity without value.

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
        machine.start();
        machine.sendEvent(Events.PAY);
        stateMachinePersister.persist(machine, "order-1");
        StateMachine<States, Events> restored = factory.getStateMachine("order-1");
        stateMachinePersister.restore(restored, "order-1");
        assertAll(
            () -> assertEquals(States.PAID, restored.getState().getId()),
            () -> assertEquals("order-1", restored.getId())
        );
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

## Verification rule

Verify that the chosen factory or persistence path solves a real lifecycle requirement, then prove it with a save or restore test instead of assuming the machine state survives process boundaries.

## Related blockers

- Open [pseudo-states.md](pseudo-states.md) when the branching model depends on choice, junction, fork, join, or history semantics.
- Open [reactive-support.md](reactive-support.md) when the machine's actions, guards, or event flow must stay reactive.
